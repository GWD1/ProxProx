/*
 * Copyright (c) 2016, GoMint, BlackyPaw and geNAZt
 *
 * This code is licensed under the BSD license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.gomint.proxprox.network;

import io.gomint.jraknet.PacketReliability;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author geNAZt
 * @version 1.0
 */
@AllArgsConstructor
@Getter
public class CachedData {

    private PacketReliability reliability;
    private byte[] data;
    private int orderingChannel;

}
