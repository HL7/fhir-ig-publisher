package org.hl7.fhir.igtools.publisher;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.function.Consumer;

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
        File fshIni = new File(Utilities.path(file.getAbsolutePath(), "fsh.ini"));
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
            try (FileInputStream in = new FileInputStream(fshIni)) {
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
        exec.setWorkingDirectory(file);
        ExecuteWatchdog watchdog = new ExecuteWatchdog(fshTimeout);
        exec.setWatchdog(watchdog);
        
        try {
            final String execString;
            if (SystemUtils.IS_OS_WINDOWS) {
                exec.execute(getWindowsCommandLine(fshVersion, mode));
            } else if (FhirSettings.hasNpmPath()) {
                Map<String, String> vars = new HashMap<>(System.getenv());
                vars.put("PATH", FhirSettings.getNpmPath()+":"+vars.get("PATH"));

                exec.execute(getNpmPathCommandLine(fshVersion, mode), vars);
            } else {
                exec.execute(getDefaultCommandLine(fshVersion, mode));
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
        }
        if (pumpHandler.getErrorCount() > 0) {
            throw new IOException("Sushi failed with errors. Complete output from running Sushi : " + pumpHandler.getBufferString());
        }
    }


    @Nonnull
    protected CommandLine getDefaultCommandLine(String fshVersion, PublisherUtils.IGBuildMode mode) {
        final List<String> sushiCommandList = getSushiCommandList(fshVersion, mode);
        CommandLine commandLine = new CommandLine(sushiCommandList.get(0));
        for (int i = 1; i < sushiCommandList.size(); i++) {
            commandLine.addArgument(sushiCommandList.get(i));
        }
        commandLine.addArgument(".").addArgument("-o").addArgument(".");
        return commandLine;
    }

    @Nonnull
    protected CommandLine getNpmPathCommandLine(String fshVersion, PublisherUtils.IGBuildMode mode) {
        // bash is only here so that sushi/npx is found on the PATH set from the npm path. The arguments go to bash
        // separately - "$0" is the command and "$@" the rest - never as part of the script, so the shell doesn't read
        // anything in them (the sushi version comes from the IG's fsh.ini)
        CommandLine commandLine = new CommandLine("bash")
          .addArgument("-c")
          .addArgument("exec \"$0\" \"$@\"", false);
        for (String argument : getSushiCommandList(fshVersion, mode)) {
            commandLine.addArgument(argument, false);
        }
        commandLine.addArgument(".", false).addArgument("-o", false).addArgument(".", false);
        return commandLine;
    }

    @Nonnull
    protected CommandLine getWindowsCommandLine(String fshVersion, PublisherUtils.IGBuildMode mode) {
        CommandLine commandLine = new CommandLine("cmd").addArgument("/C");
        for (String argument : getSushiCommandList(fshVersion,mode)) {
            commandLine.addArgument(argument);
        }
        commandLine.addArgument(".").addArgument("-o").addArgument(".");
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
