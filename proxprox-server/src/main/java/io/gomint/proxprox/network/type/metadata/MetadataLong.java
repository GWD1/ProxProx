/*
 * Copyright (c) 2017, GoMint, BlackyPaw and geNAZt
 *
 * This code is licensed under the BSD license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.gomint.proxprox.network.type.metadata;

import io.gomint.jraknet.PacketBuffer;
import lombok.ToString;

/**
 * @author BlackyPaw
 * @version 1.0
 */
@ToString
public class MetadataLong extends MetadataValue {

    private long value;

    /**
     * Constructs a new metadata int
     */
    MetadataLong() {

    }

    /**
     * Constructs a new metadata int and initializes it with the specified value.
     *
     * @param value The value to initialize the metadata int with
     */
    public MetadataLong( long value ) {
        this.value = value;
    }

    /**
     * Gets the value of this metadata int.
     *
     * @return The value of this metadata int
     */
    public long getValue() {
        return this.value;
    }

    /**
     * Sets the value of this metadata int.
     *
     * @param value The value of this metadata int
     */
    public void setValue( long value ) {
        this.value = value;
    }

    // ========================== METADATA VALUE ========================== //
    @Override
    void serialize( PacketBuffer buffer, int index ) {
        super.serialize( buffer, index );
        buffer.writeSignedVarLong( this.value );
    }

    @Override
    void deserialize( PacketBuffer buffer ) {
        this.value = buffer.readSignedVarLong().longValue();
    }

    @Override
    byte getTypeId() {
        return MetadataContainer.METADATA_LONG;
    }

}
