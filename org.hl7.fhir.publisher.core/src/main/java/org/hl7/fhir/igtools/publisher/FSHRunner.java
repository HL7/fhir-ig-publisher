package org.hl7.fhir.igtools.publisher;

import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.utilities.IniFile;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.settings.FhirSettings;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

public class FSHRunner {

    private final IWorkerContext.ILoggingService logger;

    private static final long FSH_TIMEOUT = 60000 * 5; // 5 minutes....

    private long fshTimeout = FSH_TIMEOUT;

    public FSHRunner(IWorkerContext.ILoggingService logger) {
        this.logger = logger;
    }

    public void log(String string) {
        logger.logMessage(string);
    }

    protected void runFsh(File file, Publisher.IGBuildMode mode) throws IOException {
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
            IniFile ini = new IniFile(new FileInputStream(fshIni));
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
        MySushiHandler pumpHandler = new MySushiHandler();
        PumpStreamHandler pump = new PumpStreamHandler(pumpHandler);
        exec.setStreamHandler(pump);
        exec.setWorkingDirectory(file);
        ExecuteWatchdog watchdog = new ExecuteWatchdog(fshTimeout);
        exec.setWatchdog(watchdog);

        //FIXME This construction is a mess, and should use CommandLine(...).addArgument(...) construction. -Dotasek
        String cmd = fshVersion == null ? "sushi" : "npx fsh-sushi@"+fshVersion;
        if (mode == Publisher.IGBuildMode.PUBLICATION || mode == Publisher.IGBuildMode.AUTOBUILD) {
            cmd += " --require-latest";
        }
        try {
            if (SystemUtils.IS_OS_WINDOWS) {
                exec.execute(org.apache.commons.exec.CommandLine.parse("cmd /C "+cmd+" . -o ."));
            } else if (FhirSettings.hasNpmPath()) {
                ProcessBuilder processBuilder = new ProcessBuilder(new String("bash -c "+cmd));
                Map<String, String> env = processBuilder.environment();
                Map<String, String> vars = new HashMap<>();
                vars.putAll(env);
                String path = FhirSettings.getNpmPath()+":"+env.get("PATH");
                vars.put("PATH", path);
                exec.execute(org.apache.commons.exec.CommandLine.parse("bash -c "+cmd+" . -o ."), vars);
            } else {
                exec.execute(org.apache.commons.exec.CommandLine.parse(cmd+" . -o ."));
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
        if (pumpHandler.errorCount > 0) {
            throw new IOException("Sushi failed with errors. Complete output from running Sushi : " + pumpHandler.getBufferString());
        }
    }

    public static class MySushiHandler extends OutputStream {

        private final StringBuilder buffer;
        private int errorCount = -1;

        public MySushiHandler() {
            buffer = new StringBuilder(256);
        }

        public String getBufferString() {
            return this.buffer.toString();
        }

        private boolean passSushiFilter(String s) {
            if (Utilities.noString(s) || s.isBlank())
                return false;
            return true;
        }

        @Override
        public void write(int b) throws IOException {
            this.buffer.appendCodePoint(b);
            if (b == 10) { // eoln
                final String s = this.getBufferString();
                if (passSushiFilter(s)) {
                    log("Sushi: "+ StringUtils.stripEnd(s, null));
                    if (s.trim().startsWith("Errors:")) {
                        errorCount = Integer.parseInt(s.substring(10).trim());
                    }
                }
                this.buffer.setLength(0);
            }
        }
    }
}
