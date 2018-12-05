package io.gomint.proxprox.network.protocol;

import io.gomint.jraknet.PacketBuffer;
import io.gomint.proxprox.api.network.Packet;
import io.gomint.proxprox.inventory.ItemStack;
import io.gomint.proxprox.network.Protocol;
import lombok.Data;

/**
 * @author geNAZt
 * @version 1.0
 */
@Data
public class PacketMobEquipment extends Packet {

    private long entityId;

    private ItemStack stack;
    private byte slot;
    private byte selectedSlot;
    private byte windowId;

    public PacketMobEquipment() {
        super( Protocol.PACKET_MOB_EQUIPMENT );
    }

    @Override
    public void serialize( PacketBuffer buffer, int protocolVersion ) {
        buffer.writeUnsignedVarLong( this.entityId );
        writeItemStack( this.stack, buffer );
        buffer.writeByte( this.slot );
        buffer.writeByte( this.selectedSlot );
        buffer.writeByte( this.windowId );
    }

    @Override
    public void deserialize( PacketBuffer buffer, int protocolVersion ) {
        this.entityId = buffer.readUnsignedVarLong();
        this.stack = readItemStack( buffer );
        this.slot = buffer.readByte();
        this.selectedSlot = buffer.readByte();
        this.windowId = buffer.readByte();
    }

}
