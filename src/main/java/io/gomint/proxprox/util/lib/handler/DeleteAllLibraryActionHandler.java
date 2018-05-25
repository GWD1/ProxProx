package io.gomint.proxprox.util.lib.handler;

import org.apache.logging.log4j.core.util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static io.gomint.proxprox.util.lib.LibraryHelper.LOGGER;

public class DeleteAllLibraryActionHandler implements LibraryActionHandler {

    @Override
    public void handleAction(String action, File workingDirectory, String... args) {
        if (!"deleteAll".equalsIgnoreCase(action)) {
            LOGGER.warn("Triggered " + getClass().getName() + ": Action[" + action + "] does not equal 'deleteAll'");
            LOGGER.warn("Unexpected behaviour may occur. Continuing.");
        }

        if (args == null) { LOGGER.error("Failed handling action: 'args' is referring to null"); return; }

        if (workingDirectory.exists()) {
            try {
                Files.walk(workingDirectory.toPath()).filter(path -> {
                    String fileExtension = FileUtils.getFileExtension(path.toFile());
                    return "jar".equalsIgnoreCase(fileExtension);
                }).forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        LOGGER.warn("Failed deleting library: ", e);
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            LOGGER.warn("Given library directory does not exist: " + workingDirectory.getAbsolutePath());
        }
    }

}
