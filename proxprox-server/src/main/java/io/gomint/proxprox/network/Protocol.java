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

    public static final int MINECRAFT_PE_BETA_PROTOCOL_VERSION = -1;
    public static final int MINECRAFT_PE_PROTOCOL_VERSION = 340;
    public static final String MINECRAFT_PE_NETWORK_VERSION = "1.10.0";

    /**
     * Packet ID of the login packet
     */
    public static final byte PACKET_LOGIN = (byte) 0x01;

    /**
     * Packet ID of the status packet
     */
    public static final byte PACKET_PLAY_STATE = (byte) 0x02;
    public static final byte PACKET_ENCRYPTION_REQUEST = (byte) 0x03;
    public static final byte PACKET_ENCRYPTION_READY = (byte) 0x04;
    public static final byte PACKET_DISCONNECT = (byte) 0x05;
    public static final byte PACKET_RESOURCEPACK_INFO = (byte) 0x06;
    public static final byte PACKET_RESOURCEPACK_STACK = (byte) 0x07;
    public static final byte PACKET_RESOURCEPACK_RESPONSE = (byte) 0x08;
    public static final byte PACKET_TEXT = (byte) 0x09;
    public static final byte PACKET_START_GAME = (byte) 0x0b;

    /**
     * Packet ID of the batch packet
     */
    public static final byte PACKET_BATCH = (byte) 0xFE;

    /**
     * Packet ID for Player movement
     */
    public static final byte PACKET_MOVE_PLAYER = (byte) 0x13;
    public static final byte PACKET_MOB_EFFECT = (byte) 0x1C;
    public static final byte PACKET_INVENTORY_TRANSACTION = (byte) 0x1E;
    public static final byte PACKET_MOB_EQUIPMENT = (byte) 0x1F;

    /**
     * Packet ID for Player Add
     */
    public static final byte ADD_PLAYER_PACKET = (byte) 0x0c;

    /**
     * Packet ID for Player List Add / Remove
     */
    public static final byte PACKET_PLAYER_LIST = (byte) 0x3F;

    /**
     * Packet ID for Entity Add
     */
    public static final byte ADD_ENTITY_PACKET = (byte) 0x0d;

    /**
     * Packet ID for Scoreboards
     */
    public static final byte PACKET_REMOVE_OBJECTIVE = (byte) 0x6a;
    public static final byte PACKET_SET_OBJECTIVE = (byte) 0x6b;
    public static final byte PACKET_SET_SCORE = (byte) 0x6c;
    public static final byte PACKET_SET_SCOREBOARD_IDENTITY = (byte) 0x70;

    /**
     * Packet ID for Entity Remove
     */
    public static final byte REMOVE_ENTITY_PACKET = (byte) 0x0e;
    public static final byte ADD_ITEM_ENTITY = (byte) 0x0f;

    public static final byte PACKET_ENTITY_METADATA = (byte) 0x27;

    public static final byte PACKET_SET_DIFFICULTY = (byte) 0x3C;
    public static final byte PACKET_SET_GAMEMODE = (byte) 0x3E;

    public static final byte PACKET_SET_CHUNK_RADIUS = (byte) 0x45;
    public static final byte PACKET_TRANSFER = (byte) 0x55;
    public static final byte PACKET_SET_LOCAL_PLAYER_INITIALIZED = (byte) 0x71;

}
