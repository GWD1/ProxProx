/*
 * Copyright (c) 2016, GoMint, BlackyPaw and geNAZt
 *
 * This code is licensed under the BSD license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.gomint.proxprox.network;

import io.gomint.jraknet.PacketBuffer;
import io.gomint.jraknet.PacketReliability;
import io.gomint.jraknet.datastructures.TriadRange;
import io.gomint.proxprox.network.protocol.PacketBatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.InflaterInputStream;

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
    protected boolean handleBatchPacket( PacketBuffer buffer, PacketReliability reliability, int orderingChannel, boolean batch ) {
        if ( batch ) {
            logger.error( "Malformed batch packet payload: Batch packets are not allowed to contain further batch packets" );
            return true;
        }

        int compressedSize = buffer.readInt();              // Compressed payload length (not of interest; only uncompressed size matters)

        InflaterInputStream inflaterInputStream = new InflaterInputStream( new ByteArrayInputStream( buffer.getBuffer(), buffer.getPosition(), compressedSize ) );

        ByteArrayOutputStream bout = new ByteArrayOutputStream( compressedSize );
        byte[] batchIntermediate = new byte[256];

        try {
            int read;
            while ( ( read = inflaterInputStream.read( batchIntermediate ) ) > -1 ) {
                bout.write( batchIntermediate, 0, read );
            }
        } catch ( IOException e ) {
            // Check if we have a debugger attached
            if ( this instanceof UpstreamConnection ) {
                try {
                    ((UpstreamConnection) this).getNetworkDebugger().print( new FileOutputStream( "debug/" + System.currentTimeMillis() + ".dbg" ) );
                } catch ( FileNotFoundException e1 ) {
                    e1.printStackTrace();
                }
            }

            logger.error( "Failed to decompress batch packet", e );
            return true;
        }

        byte[] payload = bout.toByteArray();

        boolean changed = false;
        List<TriadRange> skipBytes = null;
        PacketBuffer payloadBuffer = new PacketBuffer( payload, 0 );
        while ( payloadBuffer.getRemaining() > 0 ) {
            int beforePosition = payloadBuffer.getPosition();
            int packetLength = payloadBuffer.readInt();
            int expectedPosition = payloadBuffer.getPosition() + packetLength;

            if ( handlePacket( payloadBuffer, reliability, orderingChannel, true ) ) {
                if ( skipBytes == null ) {
                    skipBytes = new ArrayList<>();
                }

                skipBytes.add( new TriadRange( beforePosition, expectedPosition ) );
                changed = true;
            }

            payloadBuffer.skip( expectedPosition - payloadBuffer.getPosition() );
        }

        // Do we need to rebatch?
        if ( !changed ) {
            return false;
        } else {
            // Check how many bytes we skipped
            int skipped = 0;
            for ( TriadRange skipByte : skipBytes ) {
                skipped += skipByte.getMax() - skipByte.getMin();
            }

            // Do we have data?
            if ( skipped == payload.length ) {
                return true;
            }

            // New combined bytes
            byte[] newbytes = new byte[payload.length - skipped];
            int startByte = 0;
            int currentPos = 0;
            for ( TriadRange skipByte : skipBytes ) {
                System.arraycopy( payload, startByte, newbytes, currentPos, skipByte.getMin() - startByte );
                currentPos += skipByte.getMin() - startByte;
                startByte = skipByte.getMax();
            }

            // There is data to rebatch
            byte[] newBatchContent = batch( newbytes );
            PacketBatch packetBatch = new PacketBatch();
            packetBatch.setPayload( newBatchContent );

            PacketBuffer packetBuffer = new PacketBuffer( packetBatch.estimateLength() + 2 );
            packetBuffer.writeByte( (byte) 0xFE );
            packetBuffer.writeByte( packetBatch.getId() );
            packetBatch.serialize( buffer );

            announceRewrite( reliability, orderingChannel, buffer.getBuffer() );
            return true;
        }
    }

    private byte[] batch( byte[] data ) {
        Deflater deflater = new Deflater( 7 );
        deflater.setInput( data );
        deflater.finish();

        ByteArrayOutputStream baos = new ByteArrayOutputStream( data.length );
        byte[] intermediate = new byte[256];
        while ( !deflater.finished() ) {
            int read = deflater.deflate( intermediate );
            baos.write( intermediate, 0, read );
        }

        return baos.toByteArray();
    }

    /**
     * If the internal batch packet wants to redirect inner packets
     *
     * @param reliability     How reliable must the packet be rewriten?
     * @param orderingChannel The ordering channel in which we want to rewrite
     * @param batch           The packet which should be redirected
     */
    protected abstract void announceRewrite( PacketReliability reliability, int orderingChannel, byte[] batch );

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
