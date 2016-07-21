/*
 * Copyright (c) 2016, GoMint, BlackyPaw and geNAZt
 *
 * This code is licensed under the BSD license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.gomint.proxprox.network;

import io.gomint.jraknet.PacketBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * @author geNAZt
 * @version 1.0
 */
public abstract class AbstractConnection {

    private static final Logger logger = LoggerFactory.getLogger( AbstractConnection.class );

    // Used for BatchPacket decompression; stored here in order to save allocations at runtime:
    private Inflater batchDecompressor;
    private byte[] batchIntermediate;

    protected ConnectionState state = ConnectionState.HANDSHAKE;

    /**
     * Setup the internal structures needed for the Connection
     */
    protected void setup() {
        // Init structs for batch packets
        this.batchDecompressor = new Inflater();
        this.batchIntermediate = new byte[1024];
    }

    /**
     * Handles compressed batch packets directly by decoding their payload.
     *
     * @param buffer The buffer containing the batch packet's data (except packet ID)
     * @param batch  This boolean indicated if the buffer is coming out of a batched packet or not
     */
    protected void handleBatchPacket( PacketBuffer buffer, boolean batch ) {
        if ( batch ) {
            logger.error( "Malformed batch packet payload: Batch packets are not allowed to contain further batch packets" );
            return;
        }

        buffer.skip( 4 );               // Compressed payload length (not of interest; only uncompressed size matters)

        this.batchDecompressor.reset();
        this.batchDecompressor.setInput( buffer.getBuffer(), buffer.getPosition(), buffer.getRemaining() );

        ByteArrayOutputStream bout = new ByteArrayOutputStream();

        try {
            while ( !this.batchDecompressor.finished() ) {
                int read = this.batchDecompressor.inflate( this.batchIntermediate );
                bout.write( this.batchIntermediate, 0, read );
            }
        } catch ( DataFormatException e ) {
            logger.error( "Failed to decompress batch packet", e );
            return;
        }

        byte[] payload = bout.toByteArray();

        PacketBuffer payloadBuffer = new PacketBuffer( payload, 0 );
        while ( payloadBuffer.getRemaining() > 0 ) {
            int packetLength = payloadBuffer.readInt();
            int expectedPosition = payloadBuffer.getPosition() + packetLength;

            handlePacket( payloadBuffer, true );
            payloadBuffer.skip( expectedPosition - payloadBuffer.getPosition() );
        }
    }

    /**
     * Little internal handler for packets
     *
     * @param buffer  The buffer which holds the packet
     * @param batched Boolean indicating if buffer is coming out of a batched packet
     * @return false when we want to send it to the downstream, true if we consume it
     */
    protected abstract boolean handlePacket( PacketBuffer buffer, boolean batched );

    protected enum ConnectionState {
        HANDSHAKE,
        CONNECTED
    }

}
