/*
 * Copyright (c) 2017, GoMint, BlackyPaw and geNAZt
 *
 * This code is licensed under the BSD license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.gomint.proxprox.util;

import io.gomint.jraknet.PacketBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author geNAZt
 * @version 1.0
 */
public class DumpUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger( DumpUtil.class );

    public static void dumpPacketbuffer( PacketBuffer buffer ) {
        StringBuilder lineBuilder = new StringBuilder();
        StringBuilder stringRepBuilder = new StringBuilder();
        while ( buffer.getRemaining() > 0 ) {
            for ( int i = 0; i < 64 && buffer.getRemaining() > 0; ++i ) {
                byte b = buffer.readByte();
                String hex = Integer.toHexString( ( (int) b ) & 0xFF );
                if ( hex.length() < 2 ) {
                    hex = "0" + hex;
                }

                stringRepBuilder.append( (char) (b & 0xFF) );
                lineBuilder.append( hex );
                if ( i + 1 < 64 && buffer.getRemaining() > 0 ) {
                    lineBuilder.append( " " );
                }
            }

            lineBuilder.append( " " ).append( stringRepBuilder );
            LOGGER.info( lineBuilder.toString() );
            lineBuilder = new StringBuilder();
            stringRepBuilder = new StringBuilder();
        }

        buffer.resetPosition();
    }

    public static void dumpByteArray( byte[] bytes, int skip ) {
        int count = 0;
        StringBuilder stringBuilder = new StringBuilder();

        int skipped = 0;
        for ( byte aByte : bytes ) {
            if ( skipped++ < skip ) {
                continue;
            }

            String hex = Integer.toHexString( aByte & 255 );
            if ( hex.length() == 1 ) {
                hex = "0" + hex;
            }

            stringBuilder.append( hex ).append( " " );
            if ( count++ == 32 ) {
                stringBuilder.append( "\n" );
                count = 0;
            }
        }

        System.out.println( stringBuilder );
    }

    public static void dumpByteArray( byte[] bytes ) {
        int count = 0;
        StringBuilder stringBuilder = new StringBuilder();

        for ( byte aByte : bytes ) {
            String hex = Integer.toHexString( aByte & 255 );
            if ( hex.length() == 1 ) {
                hex = "0" + hex;
            }

            stringBuilder.append( hex ).append( " " );
            if ( count++ == 32 ) {
                stringBuilder.append( "\n" );
                count = 0;
            }
        }

        System.out.println( stringBuilder );
    }

}
