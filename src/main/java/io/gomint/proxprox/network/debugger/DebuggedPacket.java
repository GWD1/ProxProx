/*
 * Copyright (c) 2016, GoMint, BlackyPaw and geNAZt
 *
 * This code is licensed under the BSD license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.gomint.proxprox.network.debugger;

import io.gomint.jraknet.PacketBuffer;
import lombok.AllArgsConstructor;

import java.io.BufferedWriter;
import java.io.IOException;

/**
 * @author geNAZt
 * @version 1.0
 */
@AllArgsConstructor
public class DebuggedPacket {

    private PacketBuffer buffer;
    private long timestamp;
    private boolean batch;

    public void print( BufferedWriter writer ) {
        try {
            byte packetId = buffer.readByte();

            writer.write( "Packet gotten on " + timestamp );
            writer.write( String.format("# Packet dump of 0x%02x%s\n", packetId, batch ? " in batch" : "" ) );

            if ( packetId == (byte) 0xFE ) {
                packetId = buffer.readByte();
                writer.write( String.format("# MCPE Packet 0x%02x%s\n", packetId, batch ? " in batch" : "" ) );
            }

            writer.write( "-------------------------------------\n" );
            writer.write( "# Textual payload\n" );
            StringBuilder lineBuilder = new StringBuilder();

            while ( buffer.getRemaining() > 0 ) {
                for ( int i = 0; i < 16 && buffer.getRemaining() > 0; ++i ) {
                    lineBuilder.append( String.format("%02x", buffer.readByte()) );
                    if ( i + 1 < 16 && buffer.getRemaining() > 0 ) {
                        lineBuilder.append( " " );
                    }
                }

                lineBuilder.append( "\n" );

                writer.write( lineBuilder.toString() );
                lineBuilder.setLength(0);
            }

            writer.write( "-------------------------------------\n" );
            writer.flush();
        } catch ( IOException e ) {
            e.printStackTrace();
        }
    }

}
