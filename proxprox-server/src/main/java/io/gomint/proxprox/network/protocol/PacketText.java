/*
 * Copyright (c) 2017, GoMint, BlackyPaw and geNAZt
 *
 * This code is licensed under the BSD license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.gomint.proxprox.network.protocol;

import io.gomint.jraknet.PacketBuffer;
import io.gomint.proxprox.api.network.Packet;
import io.gomint.proxprox.network.Protocol;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author BlackyPaw
 * @version 1.0
 */
@Data
@EqualsAndHashCode( callSuper = false )
public class PacketText extends Packet {

    private Type type;
    private String sender;
    private String message;
    private String[] arguments = new String[0];
    private String xuid = "";

    private String sourceThirdPartyName = "";
    private int sourcePlatform = 0;

    public PacketText() {
        super( Protocol.PACKET_TEXT );
    }

    @Override
    public void serialize( PacketBuffer buffer, int protocolVersion ) {
        buffer.writeByte( this.type.getId() );
        buffer.writeBoolean( false );

        // Workaround for the popup notice
        if ( this.type == Type.POPUP_NOTICE ) {
            this.message += "\n" + this.sender;
        }

        switch ( this.type ) {
            case PLAYER_CHAT:
            case WHISPER:
            case ANNOUNCEMENT:
                buffer.writeString( this.sender );

            case CLIENT_MESSAGE:
            case TIP_MESSAGE:
            case SYSTEM_MESSAGE:
                buffer.writeString( this.message );
                break;

            case POPUP_NOTICE:
            case JUKEBOX_POPUP:
            case LOCALIZABLE_MESSAGE:
                buffer.writeString( this.message );
                buffer.writeByte( (byte) this.arguments.length );
                for ( String argument : this.arguments ) {
                    buffer.writeString( argument );
                }

                break;
        }

        buffer.writeString( this.xuid );
        buffer.writeString( "" ); // TODO: Check if this is the same as in SpawnPlayer / PlayerList
    }

    @Override
    public void deserialize( PacketBuffer buffer, int protocolVersion ) {
        this.type = Type.getById( buffer.readByte() );
        buffer.readBoolean();
        switch ( this.type ) {
            case PLAYER_CHAT:
            case WHISPER:
            case ANNOUNCEMENT:
                this.sender = buffer.readString();

            case CLIENT_MESSAGE:
            case TIP_MESSAGE:
            case SYSTEM_MESSAGE:
                this.message = buffer.readString();
                break;

            case POPUP_NOTICE:
            case JUKEBOX_POPUP:
            case LOCALIZABLE_MESSAGE:
                this.message = buffer.readString();
                byte count = buffer.readByte();

                this.arguments = new String[count];
                for ( byte i = 0; i < count; ++i ) {
                    this.arguments[i] = buffer.readString();
                }

                break;
            default:
                break;
        }

        this.xuid = buffer.readString();
        buffer.readString();
    }

    public enum Type {

        /**
         * Type value for unformatted messages.
         */
        CLIENT_MESSAGE( (byte) 0 ),

        /**
         * Type value for usual player chat.
         */
        PLAYER_CHAT( (byte) 1 ),

        /**
         * Type value for localizable messages included in Minecraft's language files.
         */
        LOCALIZABLE_MESSAGE( (byte) 2 ),

        /**
         * Type value for displaying text right above a player's action bar.
         */
        POPUP_NOTICE( (byte) 3 ),

        /**
         * Type value for showing a single line of text above a player's action bar and the popup (so you can have a 3 high popup message).
         */
        JUKEBOX_POPUP( (byte) 4 ),

        /**
         * Type value for displaying text slightly below the center of the screen (similar to title
         * text of PC edition).
         */
        TIP_MESSAGE( (byte) 5 ),

        /**
         * Type value for unformatted messages. Actual use unknown, same as system, apparently.
         */
        SYSTEM_MESSAGE( (byte) 6 ),

        /**
         * This applies the whisper text in the client to the arguments sender and message
         */
        WHISPER( (byte) 7 ),

        /**
         * Seems to work like a normal client message. Maybe there is something to it (like they stay longer) but i couldn't find whats different for now
         */
        ANNOUNCEMENT( (byte) 8 );

        private final byte id;

        Type( byte id ) {
            this.id = id;
        }

        public static Type getById( byte id ) {
            switch ( id ) {
                case 0:
                    return CLIENT_MESSAGE;
                case 1:
                    return PLAYER_CHAT;
                case 2:
                    return LOCALIZABLE_MESSAGE;
                case 3:
                    return POPUP_NOTICE;
                case 4:
                    return JUKEBOX_POPUP;
                case 5:
                    return TIP_MESSAGE;
                case 6:
                    return SYSTEM_MESSAGE;
                case 7:
                    return WHISPER;
                case 8:
                    return ANNOUNCEMENT;
                default:
                    return null;
            }
        }

        public byte getId() {
            return this.id;
        }

    }
}
