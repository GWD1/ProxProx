package io.gomint.proxprox.network;

import io.gomint.jraknet.Connection;
import io.gomint.jraknet.PacketBuffer;
import io.gomint.jraknet.PacketReliability;
import io.gomint.proxprox.network.protocol.PacketBatch;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.zip.Deflater;

/**
 * @author geNAZt
 * @version 1.0
 */
@RequiredArgsConstructor
public class PostProcessWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger( PostProcessWorker.class );

    private final BatchStreamHolder batchHolder = new BatchStreamHolder();
    private final Connection connection;
    @Setter
    private EncryptionHandler encryptionHandler;

    private void writeVarInt( int value, OutputStream stream ) throws IOException {
        int copyValue = value;

        while ( ( copyValue & -128 ) != 0 ) {
            stream.write( copyValue & 127 | 128 );
            copyValue >>>= 7;
        }

        stream.write( copyValue );
    }

    public void sendPacket( PacketBuffer buffer ) {
        try {
            writeVarInt( buffer.getPosition(), this.batchHolder.getOutputStream() );
            this.batchHolder.getOutputStream().write( buffer.getBuffer(), buffer.getBufferOffset(), buffer.getPosition() - buffer.getBufferOffset() );
        } catch ( IOException e ) {
            e.printStackTrace();
        }

        PacketBatch batch = new PacketBatch();
        batch.setPayload( this.batchHolder.getBytes() );

        if ( this.encryptionHandler != null ) {
            batch.setPayload( this.encryptionHandler.isEncryptionFromServerEnabled() ? this.encryptionHandler.encryptInputForServer( batch.getPayload() ) : this.encryptionHandler.encryptInputForClient( batch.getPayload() ) );
        }

        this.batchHolder.reset();

        buffer = new PacketBuffer( 64 );
        buffer.writeByte( batch.getId() );
        batch.serialize( buffer );

        this.connection.send( PacketReliability.RELIABLE_ORDERED, batch.orderingChannel(), buffer.getBuffer(), 0, buffer.getPosition() );
    }

    public void sendPackets( List<PacketBuffer> buffers ) {
        for ( PacketBuffer buffer : buffers ) {
            try {
                writeVarInt( buffer.getPosition(), this.batchHolder.getOutputStream() );
                this.batchHolder.getOutputStream().write( buffer.getBuffer(), buffer.getBufferOffset(), buffer.getPosition() - buffer.getBufferOffset() );
            } catch ( IOException e ) {
                e.printStackTrace();
            }
        }

        PacketBatch batch = new PacketBatch();
        batch.setPayload( this.batchHolder.getBytes() );

        if ( this.encryptionHandler != null ) {
            batch.setPayload( this.encryptionHandler.isEncryptionFromServerEnabled() ? this.encryptionHandler.encryptInputForServer( batch.getPayload() ) : this.encryptionHandler.encryptInputForClient( batch.getPayload() ) );
        }

        this.batchHolder.reset();

        PacketBuffer buffer = new PacketBuffer( 64 );
        buffer.writeByte( batch.getId() );
        batch.serialize( buffer );

        this.connection.send( PacketReliability.RELIABLE_ORDERED, batch.orderingChannel(), buffer.getBuffer(), 0, buffer.getPosition() );
    }

    private final class BatchStreamHolder {

        private ByteArrayOutputStream bout;
        private final Deflater deflater;

        private BatchStreamHolder() {
            this.bout = new ByteArrayOutputStream();
            this.deflater = new Deflater( 3 );
        }

        private void reset() {
            this.bout = new ByteArrayOutputStream();
            this.deflater.reset();
            this.deflater.setInput( new byte[0] );
        }

        private OutputStream getOutputStream() {
            return this.bout;
        }

        private byte[] getBytes() {
            byte[] input = this.bout.toByteArray();
            this.deflater.setInput( input );
            this.deflater.finish();

            this.bout.reset();
            byte[] intermediate = new byte[1024];
            while ( !this.deflater.finished() ) {
                int read = this.deflater.deflate( intermediate );
                this.bout.write( intermediate, 0, read );
            }

            return this.bout.toByteArray();
        }

    }

}
