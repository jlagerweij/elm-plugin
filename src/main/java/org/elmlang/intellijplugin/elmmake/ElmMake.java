package org.elmlang.intellijplugin.elmmake;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.diagnostic.Logger;

import org.elmlang.intellijplugin.Component;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Optional;

public class ElmMake {
    private static final Logger LOG = Logger.getInstance(Component.class);

    public static Optional<InputStream> execute(String workDirectory, String elmMakeExePath, String file) {
        GeneralCommandLine commandLine = createGeneralCommandLine(elmMakeExePath);
        commandLine.setWorkDirectory(workDirectory);
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
        GeneralCommandLine commandLine = createGeneralCommandLine(elmMakeExePath);
        commandLine.addParameter("--help");
        Process process = commandLine.createProcess();
        process.waitFor();
        if (process.exitValue() == 0) {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            return bufferedReader.readLine();
        } else {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            throw new IllegalStateException(bufferedReader.readLine());
        }
    }

    @NotNull
    private static GeneralCommandLine createGeneralCommandLine(String elmMakeExePath) {
        GeneralCommandLine commandLine = new GeneralCommandLine();

        File directory = new File(elmMakeExePath).getParentFile();
        File nodeExePath = new File(directory, "node");
        if (nodeExePath.exists()) {
            commandLine.setExePath(nodeExePath.getAbsolutePath());
            commandLine.addParameter(elmMakeExePath);
        } else {
            commandLine.setExePath(elmMakeExePath);
        }
        return commandLine;
    }

}
