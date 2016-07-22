/*
 * Copyright (c) 2016, GoMint, BlackyPaw and geNAZt
 *
 * This code is licensed under the BSD license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.gomint.proxprox.network;

import io.gomint.jraknet.PacketBuffer;
import io.gomint.jraknet.PacketReliability;
import io.gomint.proxprox.network.protocol.PacketBatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * @author geNAZt
 * @version 1.0
 */
public abstract class AbstractConnection {

    private static final Logger logger = LoggerFactory.getLogger( AbstractConnection.class );

    protected ConnectionState state = ConnectionState.HANDSHAKE;

    /**
     * Setup the internal structures needed for the Connection
     */
    protected void setup() {

    }

    /**
     * Handles compressed batch packets directly by decoding their payload.
     *
     * @param buffer          The buffer containing the batch packet's data (except packet ID)
     * @param reliability     The reliability of the packet
     * @param orderingChannel The ordering channel of this batched packet
     * @param batch           This boolean indicated if the buffer is coming out of a batched packet or not
     */
    protected void handleBatchPacket( PacketBuffer buffer, PacketReliability reliability, int orderingChannel, boolean batch ) {
        if ( batch ) {
            logger.error( "Malformed batch packet payload: Batch packets are not allowed to contain further batch packets" );
            return;
        }

        buffer.skip( 4 );               // Compressed payload length (not of interest; only uncompressed size matters)

        Inflater inflater = new Inflater();
        inflater.setInput( buffer.getBuffer(), buffer.getPosition(), buffer.getRemaining() );

        ByteArrayOutputStream bout = new ByteArrayOutputStream( buffer.getBuffer().length );
        byte[] batchIntermediate = new byte[1024];

        try {
            while ( !inflater.finished() ) {
                int read = inflater.inflate( batchIntermediate );
                bout.write( batchIntermediate, 0, read );
            }
        } catch ( DataFormatException e ) {
            logger.error( "Failed to decompress batch packet", e );
            return;
        }

        byte[] payload = bout.toByteArray();

        ByteBuffer newBytes = ByteBuffer.allocate( payload.length );
        PacketBuffer payloadBuffer = new PacketBuffer( payload, 0 );
        while ( payloadBuffer.getRemaining() > 0 ) {
            int packetLength = payloadBuffer.readInt();
            int beforePosition = payloadBuffer.getPosition();
            int expectedPosition = payloadBuffer.getPosition() + packetLength;

            if ( !handlePacket( payloadBuffer, reliability, orderingChannel, true ) ) {
                newBytes.putInt( packetLength );
                byte[] chunkCopy = new byte[expectedPosition - beforePosition];
                System.arraycopy( payload, beforePosition, chunkCopy, 0, expectedPosition - beforePosition );
                newBytes.put( chunkCopy );
            }

            payloadBuffer.skip( expectedPosition - payloadBuffer.getPosition() );
        }

        if ( newBytes.position() > 0 ) {
            // There is data to rebatch
            PacketBatch packetBatch = batch( Arrays.copyOf( newBytes.array(), newBytes.position() ) );
            announceRewrite( reliability, orderingChannel, packetBatch );
        }
    }

    private PacketBatch batch( byte[] data ) {
        Deflater deflater = new Deflater( 7 );
        deflater.setInput( data );
        deflater.finish();

        ByteArrayOutputStream baos = new ByteArrayOutputStream( 256 );
        byte[] intermediate = new byte[1024];
        while ( !deflater.finished() ) {
            int read = deflater.deflate( intermediate );
            baos.write( intermediate, 0, read );
        }

        PacketBatch batch = new PacketBatch();
        batch.setPayload( baos.toByteArray() );
        return batch;
    }

    /**
     * If the internal batch packet wants to redirect inner packets
     *
     * @param reliability     How reliable must the packet be rewriten?
     * @param orderingChannel The ordering channel in which we want to rewrite
     * @param batch           The packet which should be redirected
     */
    protected abstract void announceRewrite( PacketReliability reliability, int orderingChannel, PacketBatch batch );

    /**
     * Little internal handler for packets
     *
     * @param buffer          The buffer which holds the packet
     * @param reliability     The reliability of the packet
     * @param orderingChannel The ordering channel from which this data comes
     * @param batched         Boolean indicating if buffer is coming out of a batched packet
     * @return false when we want to send it to the downstream, true if we consume it
     */
    protected abstract boolean handlePacket( PacketBuffer buffer, PacketReliability reliability, int orderingChannel, boolean batched );

    protected enum ConnectionState {
        HANDSHAKE,
        CONNECTED
    }

}
