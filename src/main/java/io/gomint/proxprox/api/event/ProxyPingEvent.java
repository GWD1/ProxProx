/*
 * Copyright (c) 2016, GoMint, BlackyPaw and geNAZt
 *
 * This code is licensed under the BSD license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.gomint.proxprox.api.event;

import io.gomint.proxprox.api.plugin.event.Event;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author geNAZt
 * @version 1.0
 */
@EqualsAndHashCode( callSuper = true )
@Data
@AllArgsConstructor
public class ProxyPingEvent extends Event {

    // MOTD
    private String motd;

    // Player counts
    private int onlinePlayers;
    private int maxPlayers;

}
