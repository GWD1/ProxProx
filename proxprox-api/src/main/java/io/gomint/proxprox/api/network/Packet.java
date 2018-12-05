/*
 * Copyright (c) 2016, GoMint, BlackyPaw and geNAZt
 *
 * This code is licensed under the BSD license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.gomint.proxprox.api.network;

import io.gomint.jraknet.PacketBuffer;
import io.gomint.proxprox.api.inventory.ItemStack;
import io.gomint.proxprox.api.math.BlockPosition;
import io.gomint.taglib.NBTReader;
import io.gomint.taglib.NBTTagCompound;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteOrder;

/**
 * @author geNAZt
 * @version 1.0
 */
public abstract class Packet {

    /**
     * Internal MC:PE id of this packet
     */
    protected final byte id;

    /**
     * Constructor for implemented Packets
     *
     * @param id The id which the Packet should use
     */
    protected Packet( byte id ) {
        this.id = id;
    }

    /**
     * Gets the packet's ID.
     *
     * @return The packet's ID
     */
    public byte getId() {
        return this.id;
    }

    /**
     * Serializes this packet into the given buffer.
     *
     * @param buffer          The buffer to serialize this packet into
     * @param protocolVersion which should be used
     */
    public abstract void serialize( PacketBuffer buffer, int protocolVersion );

    /**
     * Deserializes this packet from the given buffer.
     *
     * @param buffer          The buffer to deserialize this packet from
     * @param protocolVersion which should be used
     */
    public abstract void deserialize( PacketBuffer buffer, int protocolVersion );

    /**
     * Returns an estimate length of the packet (used for pre-allocation).
     *
     * @return The estimate length of the packet or -1 if unknown
     */
    public int estimateLength() {
        return -1;
    }

    /**
     * Returns the ordering channel to send the packet on.
     *
     * @return The ordering channel of the packet
     */
    public int orderingChannel() {
        return 0;
    }

    public boolean mustBeInBatch() {
        return true;
    }

    public BlockPosition readBlockPosition( PacketBuffer buffer ) {
        return new BlockPosition( buffer.readSignedVarInt(), buffer.readUnsignedVarInt(), buffer.readSignedVarInt() );
    }

    public void writeBlockPosition( BlockPosition position, PacketBuffer buffer ) {
        buffer.writeSignedVarInt( position.getX() );
        buffer.writeUnsignedVarInt( position.getY() );
        buffer.writeSignedVarInt( position.getZ() );
    }

    /**
     * Read a item stack from the packet buffer
     *
     * @param buffer from the packet
     * @return read item stack
     */
    public ItemStack readItemStack( PacketBuffer buffer ) {
        int id = buffer.readSignedVarInt();
        if ( id == 0 ) {
            return new ItemStack( 0, (short) 0, 0 );
        }

        int temp = buffer.readSignedVarInt();
        byte amount = (byte) ( temp & 0xFF );
        short data = (short) ( temp >> 8 );

        NBTTagCompound nbt = null;
        short extraLen = buffer.readLShort();
        if ( extraLen > 0 ) {
            ByteArrayInputStream bin = new ByteArrayInputStream( buffer.getBuffer(), buffer.getPosition(), extraLen );
            try {
                NBTReader nbtReader = new NBTReader( bin, ByteOrder.LITTLE_ENDIAN );
                // nbtReader.setUseVarint( true );
                nbt = nbtReader.parse();
            } catch ( IOException e ) {
                e.printStackTrace();
            }

            buffer.skip( extraLen );
        }

        // They implemented additional data for item stacks aside from nbt
        int countPlacedOn = buffer.readSignedVarInt();
        for ( int i = 0; i < countPlacedOn; i++ ) {
            buffer.readString();    // TODO: Implement proper support once we know the string values
        }

        int countCanBreak = buffer.readSignedVarInt();
        for ( int i = 0; i < countCanBreak; i++ ) {
            buffer.readString();    // TODO: Implement proper support once we know the string values
        }

        return new ItemStack( id, data, amount, nbt );
    }

    /**
     * Write a item stack to the packet buffer
     *
     * @param itemStack which should be written
     * @param buffer    which should be used to write to
     */
    public static void writeItemStack( ItemStack itemStack, PacketBuffer buffer ) {
        if ( itemStack == null || itemStack.getMaterial() == 0 ) {
            buffer.writeSignedVarInt( 0 );
            return;
        }

        buffer.writeSignedVarInt( itemStack.getMaterial() );
        buffer.writeSignedVarInt( ( itemStack.getData() << 8 ) + ( itemStack.getAmount() & 0xff ) );

        NBTTagCompound compound = itemStack.getNbtData();
        if ( compound == null ) {
            buffer.writeLShort( (short) 0 );
        } else {
            try {
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                compound.writeTo( byteArrayOutputStream, false, ByteOrder.LITTLE_ENDIAN );
                buffer.writeLShort( (short) byteArrayOutputStream.size() );
                buffer.writeBytes( byteArrayOutputStream.toByteArray() );
            } catch ( IOException e ) {
                e.printStackTrace();
                buffer.writeLShort( (short) 0 );
            }
        }

        // canPlace and canBreak
        buffer.writeSignedVarInt( 0 );
        buffer.writeSignedVarInt( 0 );
    }

    public void serializeHeader( PacketBuffer buffer ) {
        buffer.writeUnsignedVarInt( this.id );
    }

}