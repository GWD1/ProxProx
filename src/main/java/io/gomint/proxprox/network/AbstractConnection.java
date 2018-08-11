/*
 * Copyright (c) 2016, GoMint, BlackyPaw and geNAZt
 *
 * This code is licensed under the BSD license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.gomint.proxprox.network;

import io.gomint.jraknet.PacketBuffer;
import io.gomint.jraknet.PacketReliability;
import io.gomint.proxprox.ProxProx;
import io.gomint.proxprox.api.network.Packet;
import io.gomint.proxprox.network.protocol.PacketBatch;
import io.gomint.server.jni.NativeCode;
import io.gomint.server.jni.zlib.JavaZLib;
import io.gomint.server.jni.zlib.NativeZLib;
import io.gomint.server.jni.zlib.ZLib;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import lombok.Getter;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.DataFormatException;
import java.util.zip.InflaterInputStream;

/**
 * @author geNAZt
 * @version 1.0
 */
public abstract class AbstractConnection {

    private static final NativeCode<ZLib> ZLIB = new NativeCode<>( "zlib", JavaZLib.class, NativeZLib.class );
    private static final Logger LOGGER = LoggerFactory.getLogger( AbstractConnection.class );

    static {
        // Load zlib native
        ZLIB.load();
    }

    @Getter
    protected ConnectionState state = ConnectionState.HANDSHAKE;
    @Getter
    protected EncryptionHandler encryptionHandler = null;
    @Getter
    protected PostProcessExecutor executor = null;
    private ZLib decompressor;

    protected void initDecompressor() {
        this.decompressor = ZLIB.newInstance();
        this.decompressor.init( false, false, 7 );
    }

    /**
     * Setup the internal structures needed for the Connection
     */
    protected void setup() {
        this.executor = ProxProx.instance.getProcessExecutorService().getExecutor();
    }

    /**
     * Handles compressed batch packets directly by decoding their payload.
     *
     * @param buffer The buffer containing the batch packet's data (except packet ID)
     * @return decompressed and decrypted data
     */
    byte[] handleBatchPacket( PacketBuffer buffer ) {
        // Encrypted?
        byte[] input = new byte[buffer.getRemaining()];
        System.arraycopy( buffer.getBuffer(), buffer.getPosition(), input, 0, input.length );
        if ( this.encryptionHandler != null ) {
            input = this.encryptionHandler.isEncryptionFromServerEnabled() ? this.encryptionHandler.decryptInputFromServer( input ) : this.encryptionHandler.decryptInputFromClient( input );
            if ( input == null ) {
                // Decryption error
                disconnect( "Checksum of encrypted packet was wrong" );
                return null;
            }
        }

        ByteBuf inBuf = PooledByteBufAllocator.DEFAULT.directBuffer( input.length );
        inBuf.writeBytes( input );

        ByteBuf outBuf = PooledByteBufAllocator.DEFAULT.directBuffer( 8192 ); // We will write at least once so ensureWrite will realloc to 8192 so or so

        try {
            this.decompressor.process( inBuf, outBuf );
        } catch ( DataFormatException e ) {
            LOGGER.error( "Failed to decompress batch packet", e );
            outBuf.release();
            return null;
        } finally {
            inBuf.release();
        }

        byte[] data = new byte[outBuf.readableBytes()];
        outBuf.readBytes( data );
        outBuf.release();
        return data;
    }

    /**
     * Little internal handler for packets
     *
     * @param buffer          The buffer which holds the packet
     */
    protected abstract void handlePacket( PacketBuffer buffer );

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

    public abstract void send( Packet packet );

    public void close() {
        if ( this.executor != null ) {
            ProxProx.instance.getProcessExecutorService().releaseExecutor( this.executor );
            this.executor = null;
        }
    }

    protected enum ConnectionState {
        HANDSHAKE,
        CONNECTED,
        ENCRYPTED
    }

}
