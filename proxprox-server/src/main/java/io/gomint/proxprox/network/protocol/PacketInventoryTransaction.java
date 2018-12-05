package io.gomint.proxprox.network.protocol;

import io.gomint.jraknet.PacketBuffer;
import io.gomint.proxprox.api.network.Packet;
import io.gomint.proxprox.api.inventory.ItemStack;
import io.gomint.proxprox.api.math.BlockPosition;
import io.gomint.proxprox.api.math.Vector;
import io.gomint.proxprox.network.Protocol;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author geNAZt
 * @version 1.0
 */
@Data
public class PacketInventoryTransaction extends Packet {

    private static final Logger LOGGER = LoggerFactory.getLogger( PacketInventoryTransaction.class );

    public static final int TYPE_NORMAL = 0;
    public static final int TYPE_MISMATCH = 1;
    public static final int TYPE_USE_ITEM = 2;
    public static final int TYPE_USE_ITEM_ON_ENTITY = 3;
    public static final int TYPE_RELEASE_ITEM = 4;

    private int type;
    private NetworkTransaction[] actions;

    // Generic
    private int actionType;
    private int hotbarSlot;
    private ItemStack itemInHand;

    // Type USE_ITEM / RELEASE_ITEM
    private BlockPosition blockPosition;
    private int face;
    private Vector playerPosition;
    private Vector clickPosition;

    // Type USE_ITEM_ON_ENTITY
    private long entityId;
    private Vector vector1;
    private Vector vector2;

    /**
     * Construct a new packet
     */
    public PacketInventoryTransaction() {
        super( Protocol.PACKET_INVENTORY_TRANSACTION );
    }

    @Override
    public void serialize( PacketBuffer buffer, int protocolVersion ) {
        buffer.writeUnsignedVarInt( this.type );

        // Write actions
        buffer.writeUnsignedVarInt( this.actions.length );
        for ( NetworkTransaction action : this.actions ) {
            action.serialize( buffer );
        }

        // Write transaction data
        switch ( this.type ) {
            case TYPE_NORMAL:
            case TYPE_MISMATCH:
                break;
            case TYPE_USE_ITEM:
                buffer.writeUnsignedVarInt( this.actionType );
                writeBlockPosition( this.blockPosition, buffer );
                buffer.writeSignedVarInt( this.face );
                buffer.writeSignedVarInt( this.hotbarSlot );
                writeItemStack( this.itemInHand, buffer );
                buffer.writeLFloat( this.playerPosition.getX() );
                buffer.writeLFloat( this.playerPosition.getY() );
                buffer.writeLFloat( this.playerPosition.getZ() );
                buffer.writeLFloat( this.clickPosition.getX() );
                buffer.writeLFloat( this.clickPosition.getY() );
                buffer.writeLFloat( this.clickPosition.getZ() );
                break;
            case TYPE_USE_ITEM_ON_ENTITY:
                buffer.writeUnsignedVarLong( this.entityId );
                buffer.writeUnsignedVarInt( this.actionType );
                buffer.writeSignedVarInt( this.hotbarSlot );
                writeItemStack( this.itemInHand, buffer );
                buffer.writeLFloat( this.vector1.getX() );
                buffer.writeLFloat( this.vector1.getY() );
                buffer.writeLFloat( this.vector1.getZ() );
                buffer.writeLFloat( this.vector2.getX() );
                buffer.writeLFloat( this.vector2.getY() );
                buffer.writeLFloat( this.vector2.getZ() );
                break;
            case TYPE_RELEASE_ITEM:
                buffer.writeUnsignedVarInt( this.actionType );
                buffer.writeSignedVarInt( this.hotbarSlot );
                writeItemStack( this.itemInHand, buffer );
                buffer.writeLFloat( this.playerPosition.getX() );
                buffer.writeLFloat( this.playerPosition.getY() );
                buffer.writeLFloat( this.playerPosition.getZ() );
                break;
            default:
                LOGGER.warn( "Unknown transaction type: " + this.type );
        }
    }

    @Override
    public void deserialize( PacketBuffer buffer, int protocolVersion ) {
        this.type = buffer.readUnsignedVarInt();

        // Read transaction action(s)
        int actionCount = buffer.readUnsignedVarInt();
        this.actions = new NetworkTransaction[actionCount];
        for ( int i = actionCount; i > 0; i-- ) {
            NetworkTransaction networkTransaction = new NetworkTransaction();
            networkTransaction.deserialize( buffer );
            this.actions[i - 1] = networkTransaction;
        }

        // Read transaction data
        switch ( this.type ) {
            case TYPE_NORMAL:
            case TYPE_MISMATCH:
                break;
            case TYPE_USE_ITEM:
                this.actionType = buffer.readUnsignedVarInt();
                this.blockPosition = readBlockPosition( buffer );
                this.face = buffer.readSignedVarInt();
                this.hotbarSlot = buffer.readSignedVarInt();
                this.itemInHand = readItemStack( buffer );
                this.playerPosition = new Vector( buffer.readLFloat(), buffer.readLFloat(), buffer.readLFloat() );
                this.clickPosition = new Vector( buffer.readLFloat(), buffer.readLFloat(), buffer.readLFloat() );
                break;
            case TYPE_USE_ITEM_ON_ENTITY:
                this.entityId = buffer.readUnsignedVarLong();
                this.actionType = buffer.readUnsignedVarInt();
                this.hotbarSlot = buffer.readSignedVarInt();
                this.itemInHand = readItemStack( buffer );
                this.vector1 = new Vector( buffer.readLFloat(), buffer.readLFloat(), buffer.readLFloat() );
                this.vector2 = new Vector( buffer.readLFloat(), buffer.readLFloat(), buffer.readLFloat() );
                break;
            case TYPE_RELEASE_ITEM:
                this.actionType = buffer.readUnsignedVarInt();
                this.hotbarSlot = buffer.readSignedVarInt();
                this.itemInHand = readItemStack( buffer );
                this.playerPosition = new Vector( buffer.readLFloat(), buffer.readLFloat(), buffer.readLFloat() );
                break;
            default:
                LOGGER.warn( "Unknown transaction type: " + this.type );
        }
    }

    @Data
    public class NetworkTransaction {

        private static final int SOURCE_CONTAINER = 0;
        private static final int SOURCE_WORLD = 2;
        private static final int SOURCE_CREATIVE = 3;
        private static final int SOURCE_WTF_IS_DIS = 99999;

        private int sourceType;
        private int windowId;
        private int unknown; // Maybe entity id?
        private int slot;
        private ItemStack oldItem;
        private ItemStack newItem;

        /**
         * Deserialize a transaction action
         *
         * @param buffer Data from the packet
         */
        public void deserialize( PacketBuffer buffer ) {
            this.sourceType = buffer.readUnsignedVarInt();

            switch ( this.sourceType ) {
                case SOURCE_CONTAINER:
                case SOURCE_WTF_IS_DIS:
                    this.windowId = buffer.readSignedVarInt();
                    break;
                case SOURCE_WORLD:
                    this.unknown = buffer.readUnsignedVarInt();
                    break;
                case SOURCE_CREATIVE:
                    break;
                default:
                    LOGGER.warn( "Unknown source type: " + this.sourceType );
            }

            this.slot = buffer.readUnsignedVarInt();
            this.oldItem = readItemStack( buffer );
            this.newItem = readItemStack( buffer );
        }

        public void serialize( PacketBuffer buffer ) {
            buffer.writeUnsignedVarInt( this.sourceType );

            switch ( this.sourceType ) {
                case SOURCE_CONTAINER:
                case SOURCE_WTF_IS_DIS:
                    buffer.writeSignedVarInt( this.windowId );
                    break;
                case SOURCE_WORLD:
                    buffer.writeUnsignedVarInt( this.unknown );
                    break;
                case SOURCE_CREATIVE:
                    break;
                default:
                    LOGGER.warn( "Unknown source type: " + this.sourceType );
            }

            buffer.writeUnsignedVarInt( this.slot );
            writeItemStack( this.oldItem, buffer );
            writeItemStack( this.newItem, buffer );
        }

    }

}
