/*
 * Copyright (c) 2016, GoMint, BlackyPaw and geNAZt
 *
 * This code is licensed under the BSD license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.gomint.proxprox.commands;


import io.gomint.proxprox.api.command.Command;
import io.gomint.proxprox.api.command.CommandSender;
import io.gomint.proxprox.api.plugin.Plugin;
import io.gomint.proxprox.plugin.PluginManager;

import java.util.Collection;

/**
 * @author geNAZt
 * @version 1.0
 */
public class Commandplugins extends Command {

    private final PluginManager pluginManager;

    /**
     * This commands prints basic information about the loaded plugins
     *
     * @param pluginManager The plugin manager which holds the plugins
     */
    public Commandplugins( PluginManager pluginManager ) {
        super( "plugins", "Show all currently enabled plugins", "pl" );
        this.pluginManager = pluginManager;
    }

    @Override
    public void execute( CommandSender sender, String[] args ) {
        Collection<Plugin> plugins = pluginManager.getPlugins();
        sender.sendMessage( "Plugins (" + plugins.size() + ")" );

        for ( Plugin plugin : plugins ) {
            sender.sendMessage( " - " + plugin.getMeta().getName() + " version " + plugin.getMeta().getVersion() );
        }
    }

}
