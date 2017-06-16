package org.elmlang.intellijplugin.elmmake;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.diagnostic.Logger;

import org.elmlang.intellijplugin.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Optional;

public class ElmMake {
    private static final Logger LOG = Logger.getInstance(Component.class);

    public static Optional<InputStream> execute(String workDirectory, String elmMakeExePath, String file) {
        GeneralCommandLine commandLine = new GeneralCommandLine();
        commandLine.setWorkDirectory(workDirectory);
        commandLine.setExePath(elmMakeExePath);
        commandLine.addParameter("--report=json");
        commandLine.addParameter("--output=/dev/null");
        commandLine.addParameter("--yes");
        commandLine.addParameter("--warn");
        commandLine.addParameter(file);

        try {
            LOG.info(commandLine.getCommandLineString(elmMakeExePath));

            Process process = commandLine.createProcess();

            process.waitFor();
            LOG.debug("elm-make exit value: " + process.exitValue());
            if (process.exitValue() == 1) {
                return Optional.of(process.getInputStream());
            }

        } catch (ExecutionException | InterruptedException e) {
            LOG.error(e.getMessage(), e);
        }
        return Optional.empty();
    }

    public static String getVersion(String elmMakeExePath) throws Exception {
        GeneralCommandLine commandLine = new GeneralCommandLine();
        commandLine.setExePath(elmMakeExePath);
        commandLine.addParameter("--help");
        Process process = commandLine.createProcess();
        process.waitFor();
        if (process.exitValue() == 0) {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            return bufferedReader.readLine();
        }

        throw new IllegalStateException("Unknown error. Elm-make exit with code: " + process.exitValue());
    }

}
