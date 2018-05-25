package io.gomint.proxprox.util.lib.handler;

import java.io.File;

import static io.gomint.proxprox.util.lib.LibraryHelper.LOGGER;

public class DeleteLibraryActionHandler implements LibraryActionHandler {


    @Override
    public void handleAction(String action, File workingDirectory, String... args) {
        if (!"delete".equalsIgnoreCase(action)) {
            LOGGER.warn("Triggered " + getClass().getName() + ": Action[" + action + "] does not equal 'delete'");
            LOGGER.warn("Unexpected behaviour may occur. Continuing.");
        }

        if (args == null) { LOGGER.error("Failed handling action: 'args' is referring to null"); return; }

        String libraryName = args[0];
        File toDelete = new File(workingDirectory, libraryName);

        if (toDelete.exists()) {
            if (!toDelete.delete()) {
                LOGGER.error("Failed deleting old version of library. Please delete {} manually", libraryName);
                System.exit(-1);
            } else {
                LOGGER.info("Deleting old version of library {}", libraryName);
            }
        }
    }

}
