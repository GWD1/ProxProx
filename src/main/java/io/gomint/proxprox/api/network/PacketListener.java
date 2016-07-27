/*
 * Copyright (c) 2016, GoMint, BlackyPaw and geNAZt
 *
 * This code is licensed under the BSD license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.gomint.proxprox.api.network;

import io.gomint.proxprox.api.entity.Player;

/**
 * @author geNAZt
 * @version 1.0
 */
public abstract class PacketListener<T> {

    public abstract void receive( Player player, T packet );

}
