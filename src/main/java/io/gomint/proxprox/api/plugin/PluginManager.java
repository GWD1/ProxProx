/*
 * Copyright (c) 2016, GoMint, BlackyPaw and geNAZt
 *
 * This code is licensed under the BSD license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.gomint.proxprox.api.plugin;

import io.gomint.proxprox.api.plugin.event.Listener;

/**
 * @author Fabian
 * @version 1.0
 */
public interface PluginManager {

    /**
     * Disable the given plugin. This is only valid to be called from {@link Plugin#disable()}
     *
     * @param plugin which should be disabled
     * @throws SecurityException when somebody else as the Main Class tries to disable a plugin
     */
    void disablePlugin( Plugin plugin );

    /**
     * Absolute path of the plugin Directory. This is used to determinate where the data folders of the Plugins
     * should reside
     *
     * @return absolute Path of the plugin folder
     */
    String getBaseDirectory();

    /**
     * Register a new event listener
     *
     * @param plugin   The plugin which would like to register the listener
     * @param listener The listener which should be registered
     */
    void registerListener( Plugin plugin, Listener listener );

}
