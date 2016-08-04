/*
 * Copyright (c) 2016, GoMint, BlackyPaw and geNAZt
 *
 * This code is licensed under the BSD license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.gomint.proxprox.network.debugger;

import io.gomint.jraknet.PacketBuffer;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * @author geNAZt
 * @version 1.0
 */
public class NetworkDebugger {

    private List<DebuggedPacket> packets = new ArrayList<>();

    public void addPacket( PacketBuffer buffer, boolean batched ) {
        this.packets.add( new DebuggedPacket( buffer, System.currentTimeMillis(), batched ) );
    }

    public void print( OutputStream out ) {
        try ( BufferedWriter writer = new BufferedWriter( new OutputStreamWriter( out ) ) ) {
            writer.write( "Network Debug (v1):\n" );
            writer.write( "Having " + this.packets.size() + " packets in report\n" );
            writer.write( "\n" );

            for ( DebuggedPacket packet : this.packets ) {
                packet.print( writer );
            }

            writer.flush();
        } catch ( IOException e ) {
            // Ignore .-.
        }
    }

}
