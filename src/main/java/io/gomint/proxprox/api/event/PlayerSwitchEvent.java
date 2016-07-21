/*
 * Copyright (c) 2016, GoMint, BlackyPaw and geNAZt
 *
 * This code is licensed under the BSD license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.gomint.proxprox.api.event;

import io.gomint.proxprox.api.entity.Player;
import io.gomint.proxprox.api.entity.Server;
import io.gomint.proxprox.api.plugin.event.Event;
import io.gomint.proxprox.api.data.ServerDataHolder;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author geNAZt
 * @version 1.0
 *
 * Event which gets emitted when a User is about to join a new Downstream Server
 */
@AllArgsConstructor
public class PlayerSwitchEvent extends Event {

    @Getter private final Player player;
    @Getter private final Server from;
    @Getter private Server to;

    /**
     * Set the new target for this user
     *
     * @param server The new target server
     */
    public void setTo( ServerDataHolder server ) {
        this.to = server;
    }

}
