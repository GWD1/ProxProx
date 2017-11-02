/*
 * Copyright (c) 2016, GoMint, BlackyPaw and geNAZt
 *
 * This code is licensed under the BSD license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.gomint.proxprox;

import io.gomint.proxprox.api.entity.Player;
import io.gomint.proxprox.api.network.NetworkChannels;

import java.util.UUID;

/**
 * @author geNAZt
 * @version 1.0
 */
public interface Proxy {

    /**
     * Get the current running Proxy
     *
     * @return The current running Proxy Instance
     */
    static Proxy getInstance() {
        return ProxProx.instance;
    }

    /**
     * Get the Player
     *
     * @param uuid The uuid of the player we want to look up
     * @return The player or null when not found
     */
    Player getPlayer( UUID uuid );

    NetworkChannels getNetworkChannels();

}
