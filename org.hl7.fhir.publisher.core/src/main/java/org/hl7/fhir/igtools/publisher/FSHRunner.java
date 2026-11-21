package org.hl7.fhir.igtools.publisher;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.function.Consumer;
import java.util.stream.Stream;

import javax.annotation.Nonnull;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.utilities.IniFile;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.logging.ILoggingService;
import org.hl7.fhir.utilities.settings.FhirSettings;

import com.google.common.collect.ImmutableList;
import org.hl7.fhir.utilities.filesystem.ManagedFileAccess;

public class FSHRunner {

    private final ILoggingService logger;

    private static final long FSH_TIMEOUT = 60000 * 5; // 5 minutes....

    private long fshTimeout = FSH_TIMEOUT;

    public FSHRunner(ILoggingService logger) {
        this.logger = logger;
    }

    public void log(String string) {
        logger.logMessage(string);
    }

    protected void runFsh(File file, PublisherUtils.IGBuildMode mode) throws IOException {
        runFsh(file, null, mode);
    }

    /**
     * @param file the folder sushi is run on
     * @param igRoot the IG's root folder, if that's not file
     */
    protected void runFsh(File file, File igRoot, PublisherUtils.IGBuildMode mode) throws IOException {
        checkNoNpmContent(file, igRoot);
        File fshIni = ManagedFileAccess.file(Utilities.path(file.getAbsolutePath(), "fsh.ini"));
        /* Changed 2023-03-20 by dotasek. If the fshIni file resolution below is still commented out
        // by 2023-06-20, delete the code below.
        if (!fshIni.exists()) {
            try {
                fshIni = new File(Utilities.path(Utilities.getDirectoryForFile(file.getAbsolutePath()), "fsh.ini"));
            } catch (RuntimeException e) {
                logger.logDebugMessage(IWorkerContext.ILoggingService.LogCategory.INIT,  "Could not check parent directory of file " + file.getAbsolutePath() + " " + e.getMessage());
            }
        }
        */
        String fshVersion = null;
        if (fshIni.exists()) {
            IniFile ini;
            try (FileInputStream in = ManagedFileAccess.inStream(fshIni)) {
              ini = new IniFile(in);
            }
            if (ini.hasProperty("FSH", "timeout")) {
                fshTimeout = ini.getLongProperty("FSH", "timeout") * 1000;
            }
            if (ini.hasProperty("FSH", "sushi-version")) {
                fshVersion = ini.getStringProperty("FSH", "sushi-version");
            }
        }
        log("Run Sushi on "+file.getAbsolutePath());
        DefaultExecutor exec = new DefaultExecutor();
        exec.setExitValue(0);
        MySushiHandler pumpHandler = new MySushiHandler(this::log);
        PumpStreamHandler pump = new PumpStreamHandler(pumpHandler);
        exec.setStreamHandler(pump);
        // npm takes its configuration (.npmrc, including the registry it installs from) and the packages it runs
        // (node_modules) from the folder it's run in and the folders above it, so it isn't run in the IG's folder, or
        // anywhere below it: it's run in an empty folder of its own, with a random name, and told where the IG is
        File workDir = makeWorkFolder();
        exec.setWorkingDirectory(workDir);
        ExecuteWatchdog watchdog = new ExecuteWatchdog(fshTimeout);
        exec.setWatchdog(watchdog);
        String folder = file.getAbsolutePath();
        
        try {
            if (SystemUtils.IS_OS_WINDOWS) {
                exec.execute(getWindowsCommandLine(fshVersion, mode, folder), PublisherUtils.noCurrentFolderLookup(System.getenv()));
            } else if (FhirSettings.hasNpmPath()) {
                Map<String, String> vars = new HashMap<>(System.getenv());
                vars.put("PATH", FhirSettings.getNpmPath()+":"+vars.get("PATH"));

                exec.execute(getNpmPathCommandLine(fshVersion, mode, folder), vars);
            } else {
                exec.execute(getDefaultCommandLine(fshVersion, mode, folder));
            }
        } catch (IOException ioex) {
            log("Sushi couldn't be run. Complete output from running Sushi : " + pumpHandler.getBufferString());
            if (watchdog.killedProcess()) {
                log("Sushi timeout exceeded: " + Long.toString(fshTimeout/1000) + " seconds");
            } else {
                log("Note: Check that Sushi is installed correctly (\"npm install -g fsh-sushi\". On windows, get npm from https://www.npmjs.com/get-npm)");
            }
            log("Exception: "+ioex.getMessage());
            throw ioex;
        } finally {
            deleteQuietly(workDir);
        }
        if (pumpHandler.getErrorCount() > 0) {
            throw new IOException("Sushi failed with errors. Complete output from running Sushi : " + pumpHandler.getBufferString());
        }
    }


    /**
     * npm would use an .npmrc in the IG (which can say which registry packages come from), or a fsh-sushi in the IG's
     * node_modules, to decide what runs as sushi. It isn't run in the IG's folder any more, so it wouldn't find them,
     * but an IG that has them is refused anyway: they can only be there to change what runs
     */
    static void checkNoNpmContent(File... folders) throws IOException {
        for (File f : folders) {
            if (f == null) {
                continue;
            }
            if (ManagedFileAccess.file(f, ".npmrc").exists()) {
                throw new FHIRException("Sushi can't be run: the IG has an .npmrc file ("+new File(f, ".npmrc").getAbsolutePath()+"), which would change what npm runs. Remove it");
            }
            if (ManagedFileAccess.file(new File(f, "node_modules"), "fsh-sushi").exists()) {
                throw new FHIRException("Sushi can't be run: the IG has its own copy of sushi ("+new File(new File(f, "node_modules"), "fsh-sushi").getAbsolutePath()+"). Remove it");
            }
        }
    }

    /**
     * The empty folder npm is run in. npm also looks in the folders above it (on a shared machine, another user can put
     * a node_modules/fsh-sushi or an .npmrc in /tmp), unless the folder has a package.json of its own: then npm stops
     * there
     */
    static File makeWorkFolder() throws IOException {
        File workDir = ManagedFileAccess.createTempDirectory("sushi-");
        Files.write(ManagedFileAccess.file(workDir, "package.json").toPath(), "{}".getBytes(StandardCharsets.UTF_8));
        return workDir;
    }

    private static void deleteQuietly(File dir) {
        try (Stream<Path> walk = Files.walk(dir.toPath())) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException e) {
            // it's in the temp folder
        }
    }

    @Nonnull
    protected CommandLine getDefaultCommandLine(String fshVersion, PublisherUtils.IGBuildMode mode, String folder) {
        final List<String> sushiCommandList = getSushiCommandList(fshVersion, mode);
        CommandLine commandLine = new CommandLine(sushiCommandList.get(0));
        for (int i = 1; i < sushiCommandList.size(); i++) {
            commandLine.addArgument(sushiCommandList.get(i));
        }
        commandLine.addArgument(folder, false).addArgument("-o", false).addArgument(folder, false);
        return commandLine;
    }

    @Nonnull
    protected CommandLine getNpmPathCommandLine(String fshVersion, PublisherUtils.IGBuildMode mode, String folder) {
        // bash is only here so that sushi/npx is found on the PATH set from the npm path. The arguments go to bash
        // separately - "$0" is the command and "$@" the rest - never as part of the script, so the shell doesn't read
        // anything in them (the sushi version comes from the IG's fsh.ini)
        CommandLine commandLine = new CommandLine("bash")
          .addArgument("-c")
          .addArgument("exec \"$0\" \"$@\"", false);
        for (String argument : getSushiCommandList(fshVersion, mode)) {
            commandLine.addArgument(argument, false);
        }
        commandLine.addArgument(folder, false).addArgument("-o", false).addArgument(folder, false);
        return commandLine;
    }

    @Nonnull
    protected CommandLine getWindowsCommandLine(String fshVersion, PublisherUtils.IGBuildMode mode, String folder) {
        CommandLine commandLine = new CommandLine("cmd").addArgument("/C");
        for (String argument : getSushiCommandList(fshVersion,mode)) {
            commandLine.addArgument(argument);
        }
        commandLine.addArgument(folder).addArgument("-o").addArgument(folder);
        return commandLine;
    }

    /**
     * The sushi version comes from the IG's fsh.ini, and goes to npx as fsh-sushi@{version}. npm reads far more than a
     * version there (another package - npm:name -, a git repository, a tarball URL...), and on Windows the command
     * goes through cmd, so anything but a plain version number would let the IG choose what is run
     */
    public static boolean isValidSushiVersion(String version) {
        return version != null && version.matches("[0-9]+\\.[0-9]+\\.[0-9]+(-[A-Za-z0-9]+(\\.[A-Za-z0-9]+)*)?");
    }

    protected List<String> getSushiCommandList(String fshVersion, PublisherUtils.IGBuildMode mode) {
        if (fshVersion != null && !isValidSushiVersion(fshVersion)) {
            throw new FHIRException("The sushi-version in fsh.ini must be a version number (e.g. 3.16.0), not '"+fshVersion+"'");
        }
        final List<String> cmd = fshVersion == null ? List.of("sushi"): List.of("npx", "fsh-sushi@"+fshVersion);
        if (mode == PublisherUtils.IGBuildMode.PUBLICATION || mode == PublisherUtils.IGBuildMode.AUTOBUILD) {
            return new ImmutableList.Builder<String>().addAll(cmd).add("--require-latest").build();
        }
        return cmd;
    }
    protected String getSushiCommandString(String fshVersion, PublisherUtils.IGBuildMode mode) {

        StringJoiner stringJoiner = new StringJoiner(" ");
        for (String argument : getSushiCommandList(fshVersion,mode)) {
            stringJoiner.add(argument);
        }
        return stringJoiner.toString();
    }
  
    public static class MySushiHandler extends OutputStream {

        private final ByteArrayOutputStream buffer;
        private int errorCount = -1;
        private final Consumer<String> outputConsumer;

        public MySushiHandler(final Consumer<String> outputConsumer) {
            buffer = new ByteArrayOutputStream(256);
            this.outputConsumer = Objects.requireNonNull(outputConsumer);
        }

        public String getBufferString() {
            return this.buffer.toString(StandardCharsets.UTF_8);
        }

        private boolean passSushiFilter(String s) {
            if (Utilities.noString(s) || s.isBlank())
                return false;
            return true;
        }

        @Override
        public void write(int b) throws IOException {
            if (b >= -128 && b <= 127) {
                this.buffer.write(b);
            } else {
                this.buffer.write('?');
            }
            if (b == 10) { // eoln
                final String s = this.getBufferString();
                if (passSushiFilter(s)) {
                    this.outputConsumer.accept("Sushi: "+ StringUtils.stripEnd(s, null));
                    if (s.trim().startsWith("Errors:")) {
                        errorCount = Integer.parseInt(s.substring(10).trim());
                    }
                }
                this.buffer.reset();
            }
        }

        protected int getErrorCount() {
            return errorCount;
        }
    }
}
