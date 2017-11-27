package io.gomint.proxprox.debug;

import io.gomint.jraknet.PacketBuffer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author geNAZt
 * @version 1.0
 */
public class Debugger {

    private BlockingQueue<String> persistQueue = new LinkedBlockingQueue<>();
    private FileWriter fileWriter;
    private AtomicBoolean running = new AtomicBoolean( true );

    public Debugger( long guid ) {
        File logFile = new File( "debug", guid + ".log" );
        if ( !logFile.exists() ) {
            try {
                if ( logFile.createNewFile() ) {
                    this.fileWriter = new FileWriter( logFile );

                    // Start logger thread
                    this.startLogger();
                }
            } catch ( IOException e ) {
                e.printStackTrace();
            }
        }
    }

    private void startLogger() {
        new Thread( new Runnable() {
            @Override
            public void run() {
                while ( running.get() ) {
                    String line;
                    while ( ( line = persistQueue.poll() ) != null ) {
                        try {
                            fileWriter.write( line + "\n" );
                        } catch ( IOException e ) {
                            e.printStackTrace();
                        }
                    }

                    try {
                        fileWriter.flush();
                    } catch ( IOException e ) {
                        e.printStackTrace();
                    }

                    try {
                        Thread.sleep( 10 );
                    } catch ( InterruptedException e ) {
                        e.printStackTrace();
                    }
                }

                try {
                    fileWriter.close();
                } catch ( IOException e ) {
                    e.printStackTrace();
                }
            }
        } ).start();
    }

    public void addPacket( String from, String to, byte packetId, PacketBuffer buffer ) {
        StringBuilder builder = new StringBuilder( "[PKT] " ).append( from ).append( " -> " ).append( to );
        builder.append( " 0x" ).append( Integer.toHexString( packetId & 0xFF ) ).append( ": " );

        if ( buffer.getRemaining() > 64 ) {
            builder.append( "TRUNCATED " );
        }

        int showAmount = buffer.getRemaining();
        if ( showAmount > 64 ) {
            showAmount = 64;
        }

        int oldPosition = buffer.getPosition();
        for ( int i = 0; i < showAmount; i++ ) {
            byte b = buffer.readByte();
            builder.append( "0x" ).append( Integer.toHexString( b & 0xFF ) ).append( " " );
        }

        buffer.setPosition( oldPosition );
        addLine( builder.toString() );
    }

    public void removeEntity( String from, long oldId ) {
        /*String builder = "[ER] " + from + ": " + oldId;
        addLine( builder );*/
    }

    private void addLine( String content ) {
        String line = "[" + Thread.currentThread().getName() + "] [" + System.currentTimeMillis() + "] " + content;
        this.persistQueue.add( line );
    }

    public void addEntity( String from, long oldId, long newId ) {
        /*String builder = "[EA] " + from + ": " + oldId + " -> " + newId;
        addLine( builder );*/
    }

    public void addEntityRewrite( String from, String to, byte packetId, long oldId, long newId ) {
        /*String builder = "[ER] " + from + " -> " + to + " 0x" + Integer.toHexString( packetId & 0xFF ) + ": " + oldId + " -> " + newId;
        addLine( builder );*/
    }

    public void addCustomLine( String line ) {
        addLine( line );
    }

}
