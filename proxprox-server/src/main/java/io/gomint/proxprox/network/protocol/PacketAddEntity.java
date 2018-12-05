/*
 * Copyright (c) 2016, GoMint, BlackyPaw and geNAZt
 *
 * This code is licensed under the BSD license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.gomint.proxprox.network.protocol;

import io.gomint.jraknet.PacketBuffer;
import io.gomint.proxprox.api.network.Packet;
import io.gomint.proxprox.network.Protocol;
import io.gomint.proxprox.network.type.Attribute;
import io.gomint.proxprox.network.type.metadata.MetadataContainer;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * @author geNAZt
 * @version 1.0
 */
@EqualsAndHashCode( callSuper = true )
@Data
public class PacketAddEntity extends Packet {

    private long entityId;
    private int entityType;
    private float x;
    private float y;
    private float z;
    private float velocityX;
    private float velocityY;
    private float velocityZ;
    private float pitch;
    private float yaw;
    private float headYaw;

    private List<Attribute> attributes = new ArrayList<>();
    private MetadataContainer metadataContainer;

    /**
     * Constructor for implemented Packet AddEntity
     */
    public PacketAddEntity() {
        super( Protocol.ADD_ENTITY_PACKET );
    }

    @Override
    public void serialize( PacketBuffer buffer, int protocolVersion ) {
        buffer.writeSignedVarLong( this.entityId );
        buffer.writeUnsignedVarLong( this.entityId );

        buffer.writeUnsignedVarInt( this.entityType );
        buffer.writeLFloat( this.x );
        buffer.writeLFloat( this.y );
        buffer.writeLFloat( this.z );
        buffer.writeLFloat( this.velocityX );
        buffer.writeLFloat( this.velocityY );
        buffer.writeLFloat( this.velocityZ );
        buffer.writeLFloat( this.pitch );
        buffer.writeLFloat( this.yaw );
        buffer.writeLFloat( this.headYaw );

        buffer.writeUnsignedVarInt( this.attributes.size() );
        for ( Attribute attribute : this.attributes ) {
            buffer.writeString( attribute.getKey() );
            buffer.writeLFloat( attribute.getMinValue() );
            buffer.writeLFloat( attribute.getValue() );
            buffer.writeLFloat( attribute.getMaxValue() );
        }

        this.metadataContainer.serialize( buffer );

        buffer.writeUnsignedVarInt( 0 );
    }

    @Override
    public void deserialize( PacketBuffer buffer, int protocolVersion ) {
        this.entityId = buffer.readSignedVarLong().longValue();
        buffer.readUnsignedVarLong();

        this.entityType = buffer.readUnsignedVarInt();
        this.x = buffer.readLFloat();
        this.y = buffer.readLFloat();
        this.z = buffer.readLFloat();
        this.velocityX = buffer.readLFloat();
        this.velocityY = buffer.readLFloat();
        this.velocityZ = buffer.readLFloat();
        this.pitch = buffer.readLFloat();
        this.yaw = buffer.readLFloat();
        this.headYaw = buffer.readFloat();

        int amountOfAttributes = buffer.readUnsignedVarInt();
        for ( int i = 0; i < amountOfAttributes; i++ ) {
            this.attributes.add( new Attribute( buffer.readString(), buffer.readLFloat(), buffer.readLFloat(), buffer.readLFloat() ) );
        }

        this.metadataContainer = new MetadataContainer();
        this.metadataContainer.deserialize( buffer );

        buffer.readUnsignedVarInt();
    }

}
