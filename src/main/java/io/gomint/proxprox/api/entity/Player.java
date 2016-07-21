/*
 * Copyright (c) 2016, GoMint, BlackyPaw and geNAZt
 *
 * This code is licensed under the BSD license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.gomint.proxprox.api.entity;

import java.util.UUID;

/**
 * @author geNAZt
 * @version 1.0
 */
public interface Player {

    UUID getUUID();
    String getName();
    Server getServer();
    void connect( String ip, int port );

}
