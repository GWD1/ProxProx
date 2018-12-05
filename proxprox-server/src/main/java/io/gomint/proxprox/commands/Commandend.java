/*
 * Copyright (c) 2016, GoMint, BlackyPaw and geNAZt
 *
 * This code is licensed under the BSD license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.gomint.proxprox.commands;

import io.gomint.proxprox.ProxProxProxy;
import io.gomint.proxprox.api.command.Command;
import io.gomint.proxprox.api.command.CommandSender;

/**
 * @author geNAZt
 * @version 1.0
 */
public class Commandend extends Command {

    private final ProxProxProxy proxProx;

    /**
     * A command to gracefully stop ProxProxProxy
     *
     * @param proxProx The ProxProxProxy instance to stop
     */
    public Commandend( ProxProxProxy proxProx ) {
        super( "end", "Stop ProxProxProxy" );
        this.proxProx = proxProx;
    }

    @Override
    public void execute( CommandSender sender, String[] args ) {
        // Tell Prox Prox to shut down
        if ( sender.hasPermission( "proxprox.command.end" ) ) {
            proxProx.shutdown();
        }
    }

}
