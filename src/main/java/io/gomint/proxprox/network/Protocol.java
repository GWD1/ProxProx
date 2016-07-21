/*
 * Copyright (c) 2016, GoMint, BlackyPaw and geNAZt
 *
 * This code is licensed under the BSD license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.gomint.proxprox.network;

/**
 * @author geNAZt
 * @version 1.0
 */
public class Protocol {

    /**
     * Packet ID of the login packet
     */
    public static final byte LOGIN_PACKET = (byte) 0x01;

    /**
     * Packet ID of the status packet
     */
    public static final byte PLAY_STATUS_PACKET = (byte) 0x02;

    /**
     * Packet ID of the disconnect packet
     */
    public static final byte DISONNECT_PACKET = (byte) 0x05;

    /**
     * Packet ID of the batch packet
     */
    public static final byte BATCH_PACKET = (byte) 0x06;

}
