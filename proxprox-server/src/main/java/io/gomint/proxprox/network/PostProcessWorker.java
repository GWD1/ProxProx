package io.gomint.proxprox.network;

import io.gomint.jraknet.PacketBuffer;
import io.gomint.proxprox.network.protocol.PacketBatch;
import io.gomint.server.jni.NativeCode;
import io.gomint.server.jni.zlib.JavaZLib;
import io.gomint.server.jni.zlib.NativeZLib;
import io.gomint.server.jni.zlib.ZLib;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.zip.Adler32;
import java.util.zip.DataFormatException;

/**
 * @author geNAZt
 * @version 1.0
 */
public class PostProcessWorker implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger( PostProcessWorker.class );
    private static final NativeCode<ZLib> ZLIB = new NativeCode<>( "zlib", JavaZLib.class, NativeZLib.class );
    private static final ThreadLocal<ZLib> COMPRESSOR = new ThreadLocal<>();

    static {
        ZLIB.load();
    }

    private final AbstractConnection connection;
    private final List<PacketBuffer> packets;

    public PostProcessWorker( AbstractConnection connection, List<PacketBuffer> packets ) {
        this.connection = connection;
        this.packets = packets;
    }

    private ZLib getCompressor() {
        ZLib zLib = COMPRESSOR.get();
        if ( zLib != null ) {
            return zLib;
        }

        zLib = ZLIB.newInstance();
        zLib.init( true, false, 7 );
        COMPRESSOR.set( zLib );
        return zLib;
    }

    @Override
    public void run() {
        ByteBuf inBuf = writePackets( this.packets );
        byte[] data = compress( inBuf );
        inBuf.release();

        if ( data == null ) {
            return;
        }

        PacketBatch batch = new PacketBatch();
        batch.setPayload( data );

        EncryptionHandler encryptionHandler = this.connection.getEncryptionHandler();
        if ( encryptionHandler != null && this.connection.getState() == AbstractConnection.ConnectionState.ENCRYPTED ) {
            batch.setPayload( this.connection instanceof DownstreamConnection ? encryptionHandler.encryptInputForServer( batch.getPayload() ) : encryptionHandler.encryptInputForClient( batch.getPayload() ) );
        }

        this.connection.send( batch );
    }

    private ByteBuf writePackets( List<PacketBuffer> packets ) {
        ByteBuf inBuf = newNettyBuffer();

        for ( PacketBuffer buffer : packets ) {
            writeVarInt( buffer.getPosition(), inBuf );
            inBuf.writeBytes( buffer.getBuffer(), buffer.getBufferOffset(), buffer.getPosition() - buffer.getBufferOffset() );
        }

        return inBuf;
    }

    private ByteBuf newNettyBuffer() {
        return PooledByteBufAllocator.DEFAULT.directBuffer();
    }

    private byte[] compress( ByteBuf inBuf ) {
        if ( inBuf.readableBytes() > 256 ) {
            return zlibCompress( inBuf );
        } else {
            return fastStorage( inBuf );
        }
    }

    private byte[] zlibCompress( ByteBuf inBuf ) {
        ZLib compressor = this.getCompressor();
        ByteBuf outBuf = newNettyBuffer();

        try {
            compressor.process( inBuf, outBuf );
        } catch ( DataFormatException e ) {
            LOGGER.error( "Could not compress data for network", e );
            outBuf.release();
            return null;
        }

        byte[] data = new byte[outBuf.readableBytes()];
        outBuf.readBytes( data );
        outBuf.release();
        return data;
    }

    private byte[] fastStorage( ByteBuf inBuf ) {
        byte[] data = new byte[inBuf.readableBytes() + 7 + 4];
        data[0] = 0x78;
        data[1] = 0x01;
        data[2] = 0x01;

        // Write data length
        int length = inBuf.readableBytes();
        data[3] = (byte) length;
        data[4] = (byte) ( length >>> 8 );
        length = ~length;
        data[5] = (byte) length;
        data[6] = (byte) ( length >>> 8 );

        // Write data
        inBuf.readBytes( data, 7, inBuf.readableBytes() );

        long checksum = adler32( data, 7, data.length - 11 );
        data[data.length - 4] = ( (byte) ( ( checksum >> 24 ) % 256 ) );
        data[data.length - 3] = ( (byte) ( ( checksum >> 16 ) % 256 ) );
        data[data.length - 2] = ( (byte) ( ( checksum >> 8 ) % 256 ) );
        data[data.length - 1] = ( (byte) ( checksum % 256 ) );

        return data;
    }

    /**
     * Calculates the adler32 checksum of the data
     */
    private long adler32( byte[] data, int offset, int length ) {
        final Adler32 checksum = new Adler32();
        checksum.update( data, offset, length );
        return checksum.getValue();
    }

    private void writeVarInt( int value, ByteBuf stream ) {
        int copyValue = value;

        while ( ( copyValue & -128 ) != 0 ) {
            stream.writeByte( copyValue & 127 | 128 );
            copyValue >>>= 7;
        }

        stream.writeByte( copyValue );
    }

}
