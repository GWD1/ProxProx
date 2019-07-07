package io.gomint.proxprox.network.type.metadata;

import io.gomint.jraknet.PacketBuffer;
import io.gomint.proxprox.api.network.Packet;
import io.gomint.proxprox.api.inventory.ItemStack;

/**
 * @author geNAZt
 * @version 1.0
 */
public class MetadataItem extends MetadataValue {

    private ItemStack value;

    /**
     * Constructs a new metadata item
     */
    public MetadataItem() {

    }

    /**
     * Constructs a new metadata item and initializes it with the specified value.
     *
     * @param value The value to initialize the metadata item with
     */
    public MetadataItem( ItemStack value ) {
        this.value = value;
    }

    /**
     * Gets the value of this metadata item.
     *
     * @return The value of this metadata item
     */
    public ItemStack getValue() {
        return this.value;
    }

    /**
     * Sets the value of this metadata item.
     *
     * @param value The value of this metadata item
     */
    public void setValue( ItemStack value ) {
        this.value = value;
    }

    @Override
    void serialize( PacketBuffer buffer, int index ) {
        super.serialize( buffer, index );
        Packet.writeItemStack( this.value, buffer );
    }

    @Override
    void deserialize( PacketBuffer buffer ) {
        int id = buffer.readLShort();
        byte amount = buffer.readByte();
        short data = buffer.readLShort();

        this.value = new ItemStack( id, data, amount, null );
    }

    @Override
    byte getTypeId() {
        return MetadataContainer.METADATA_ITEM;
    }

}
