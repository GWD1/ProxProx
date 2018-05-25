package io.gomint.proxprox.util.lib.handler;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import static io.gomint.proxprox.util.lib.LibraryHelper.LOGGER;

public class DownloadLibraryActionHandler implements LibraryActionHandler {

    @Override
    public void handleAction(String action, File workingDirectory, String... args) {
        if (!"download".equalsIgnoreCase(action)) {
            LOGGER.warn("Triggered " + getClass().getName() + ": Action[" + action + "] does not equal 'download'");
            LOGGER.warn("Unexpected behaviour may occur. Continuing.");
        }

        if (args == null) { LOGGER.error("Failed handling action: 'args' is referring to null"); return; }

        String libUrlRaw = args[0];

        try {
            URL libUrl = new URL(libUrlRaw);
            HttpURLConnection urlConnection = (HttpURLConnection) libUrl.openConnection();
            urlConnection.setConnectTimeout(1000);
            urlConnection.setReadTimeout(1000);

            // Head first to receive information of what type the file is
            urlConnection.setRequestMethod("HEAD");

            // Filter out non java archive content types
            if (!"application/java-archive".equals(urlConnection.getHeaderField("Content-Type"))) {
                LOGGER.debug("Skipping {}: Not a Java archive", libUrlRaw);
                return;
            }

            // We need the contentLength to compare
            int contentLength = Integer.parseInt(urlConnection.getHeaderField("Content-Length"));
            String[] tempSplit = libUrl.getPath().split("/");
            String fileName = tempSplit[tempSplit.length - 1];

            // Check if we have a file with the same length
            File libFile = new File(workingDirectory, fileName);
            if (libFile.exists() && libFile.length() == contentLength) {
                LOGGER.debug("Skipping {}: Correct sized copy already present [@" + libFile.getAbsolutePath() + "]", libUrlRaw);
                return;
            }

            // Download the file from url source
            Files.copy(libUrl.openStream(), libFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("Downloading library: {}", fileName);
        } catch (IOException e) {
            LOGGER.error("Could not download library: ", e);
        }
    }

}
