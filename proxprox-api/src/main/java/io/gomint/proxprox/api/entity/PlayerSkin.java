/*
 * Copyright (c) 2017, GoMint, BlackyPaw and geNAZt
 *
 * This code is licensed under the BSD license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.gomint.proxprox.api.entity;

import lombok.Getter;

/**
 * @author BlackyPaw
 * @version 1.0
 */
@Getter
public class PlayerSkin {

    private final String name;
    private final byte[] data;
    private final byte[] capeData;
    private final String geometryName;
    private final byte[] geometryData;

    /**
     * Create new skin
     *
     * @param name         of the skin
     * @param data         byte array of skin data ( R G B A )
     * @param capeData     byte array of the cape data (mostly null) ( R G B A )
     * @param geometryName name of the geometry to use for this skin
     * @param geometryData json data of the geometry with parents which has been sent from the client
     */
    public PlayerSkin( String name, byte[] data, byte[] capeData, String geometryName, byte[] geometryData ) {
        this.name = name;
        this.data = data;
        this.capeData = capeData;
        this.geometryName = geometryName;
        this.geometryData = geometryData;
    }

}
