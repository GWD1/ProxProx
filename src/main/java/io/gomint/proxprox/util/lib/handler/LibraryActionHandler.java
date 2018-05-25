package io.gomint.proxprox.util.lib.handler;

import java.io.File;

public interface LibraryActionHandler {

    void handleAction(String action, File workingDirectory, String... args);

}
