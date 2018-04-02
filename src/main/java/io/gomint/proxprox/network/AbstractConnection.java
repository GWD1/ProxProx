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
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
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
    protected EncryptionHandler encryptionHandler = null;

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
    void handleBatchPacket( PacketBuffer buffer, PacketReliability reliability, int orderingChannel, boolean batch ) {
        if ( batch ) {
            logger.error( "Malformed batch packet payload: Batch packets are not allowed to contain further batch packets" );
            return;
        }

        // Do we need to decrypt here?
        byte[] input = new byte[buffer.getRemaining()];
        System.arraycopy( buffer.getBuffer(), buffer.getPosition(), input, 0, input.length );
        if ( this.encryptionHandler != null ) {
            input = this.encryptionHandler.isEncryptionFromServerEnabled() ? this.encryptionHandler.decryptInputFromServer( input ) : this.encryptionHandler.decryptInputFromClient( input );
            if ( input == null ) {
                // Decryption error
                disconnect( "Checksum of encrypted packet was wrong" );
                return;
            }
        }

        InflaterInputStream inflaterInputStream = new InflaterInputStream( new ByteArrayInputStream( input ) );

        ByteArrayOutputStream bout = new ByteArrayOutputStream( buffer.getRemaining() );
        byte[] batchIntermediate = new byte[256];

        try {
            int read;
            while ( ( read = inflaterInputStream.read( batchIntermediate ) ) > -1 ) {
                bout.write( batchIntermediate, 0, read );
            }
        } catch ( IOException e ) {
            logger.error( "Failed to decompress batch packet", e );
            return;
        }

        byte[] payload = bout.toByteArray();

        PacketBuffer payloadBuffer = new PacketBuffer( payload, 0 );
        while ( payloadBuffer.getRemaining() > 0 ) {
            int packetLength = payloadBuffer.readUnsignedVarInt();

            byte[] payData = new byte[packetLength];
            payloadBuffer.readBytes( payData );
            PacketBuffer pktBuf = new PacketBuffer( payData, 0 );
            this.handlePacket( pktBuf, reliability, orderingChannel, true );

            if ( pktBuf.getRemaining() > 0 ) {
                logger.error( "Malformed batch packet payload: Could not read enclosed packet data correctly: 0x{} remaining {} bytes", Integer.toHexString( payData[0] ), pktBuf.getRemaining() );
                return;
            }
        }
    }

    /**
     * Little internal handler for packets
     *
     * @param buffer          The buffer which holds the packet
     * @param reliability     The reliability of the packet
     * @param orderingChannel The ordering channel from which this data comes
     * @param batched         Boolean indicating if buffer is coming out of a batched packet
     */
    protected abstract void handlePacket( PacketBuffer buffer, PacketReliability reliability, int orderingChannel, boolean batched );

    public abstract void disconnect( String message );

    /**
     * Parses the specified JSON string and ensures it is a JSONObject.
     *
     * @param jwt The string to parse
     * @return The parsed JSON object on success
     * @throws ParseException Thrown if the given JSON string is invalid or does not start with a JSONObject
     */
    protected JSONObject parseJwtString( String jwt ) throws ParseException {
        Object jsonParsed = new JSONParser().parse( jwt );
        if ( jsonParsed instanceof JSONObject ) {
            return (JSONObject) jsonParsed;
        } else {
            throw new ParseException( ParseException.ERROR_UNEXPECTED_TOKEN );
        }
    }

    protected enum ConnectionState {
        HANDSHAKE,
        CONNECTED
    }

}
