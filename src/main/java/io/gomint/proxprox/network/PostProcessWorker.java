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
        if ( COMPRESSOR.get() == null ) {
            ZLib zLib = ZLIB.newInstance();
            zLib.init( true, 7 );
            COMPRESSOR.set( zLib );
            return zLib;
        }

        return COMPRESSOR.get();
    }

    @Override
    public void run() {
        ZLib compressor = this.getCompressor();
        ByteBuf inBuf = PooledByteBufAllocator.DEFAULT.directBuffer();

        // Batch them first
        for ( PacketBuffer buffer : this.packets ) {
            writeVarInt( buffer.getPosition(), inBuf );
            inBuf.writeBytes( buffer.getBuffer(), buffer.getBufferOffset(), buffer.getPosition() - buffer.getBufferOffset() );
        }

        // Create the output buffer
        ByteBuf outBuf = PooledByteBufAllocator.DEFAULT.directBuffer( 8192 ); // We will write at least once so ensureWrite will realloc to 8192 so or so

        LOGGER.debug( "Compressing {} bytes", inBuf.readableBytes() );

        try {
            compressor.process( inBuf, outBuf );
        } catch ( Exception e ) {
            LOGGER.error( "Could not compress data for network", e );
            outBuf.release();
            return;
        } finally {
            inBuf.release();
        }

        byte[] data = new byte[outBuf.writerIndex()];
        outBuf.readBytes( data );
        outBuf.release();

        PacketBatch batch = new PacketBatch();
        batch.setPayload( data );

        EncryptionHandler encryptionHandler = this.connection.getEncryptionHandler();
        if ( encryptionHandler != null && this.connection.getState() == AbstractConnection.ConnectionState.ENCRYPTED ) {
            batch.setPayload( this.connection instanceof DownstreamConnection ? encryptionHandler.encryptInputForServer( batch.getPayload() ) : encryptionHandler.encryptInputForClient( batch.getPayload() ) );
        }

        this.connection.send( batch );
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
