/*
 * Copyright (c) 2016, GoMint, BlackyPaw and geNAZt
 *
 * This code is licensed under the BSD license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.gomint.proxprox.commands;

import io.gomint.proxprox.ProxProx;
import io.gomint.proxprox.api.command.Command;
import io.gomint.proxprox.api.command.CommandSender;

/**
 * @author geNAZt
 * @version 1.0
 */
public class Commandstop extends Command {

    private final ProxProx proxProx;

    /**
     * A command to gracefully stop ProxProx
     *
     * @param proxProx The ProxProx instance to stop
     */
    public Commandstop( ProxProx proxProx ) {
        super( "stop", "Stop ProxProx" );
        this.proxProx = proxProx;
    }

    @Override
    public void execute( CommandSender sender, String[] args ) {
        // Tell Prox Prox to shut down
        if ( sender.hasPermission( "proxprox.command.stop" ) ) {
            proxProx.shutdown();
        }
    }

}
