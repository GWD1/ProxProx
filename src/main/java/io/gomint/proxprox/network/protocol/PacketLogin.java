/*
 * Copyright (c) 2016, GoMint, BlackyPaw and geNAZt
 *
 * This code is licensed under the BSD license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.gomint.proxprox.network.protocol;

import io.gomint.jraknet.PacketBuffer;
import io.gomint.proxprox.network.Protocol;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;
import java.util.UUID;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * @author geNAZt
 * @version 1.0
 */
public class PacketLogin extends Packet {

	private int protocol;

	// Chain additional data
	private String userName;
	private UUID uuid;

	/**
	 * Construct a new login packet which contains all data to login into a MC:PE server
     */
	public PacketLogin() {
		super( Protocol.LOGIN_PACKET );
	}

	@Override
	public void serialize( PacketBuffer buffer ) {
		buffer.writeInt( this.protocol );
	}

	@Override
	public void deserialize( PacketBuffer buffer ) {
		this.protocol = buffer.readInt();

		// Decompress inner data (i don't know why you compress inside of a Batched Packet but hey)
		byte[] compressed = new byte[buffer.readInt()];
		buffer.readBytes( compressed );

		Inflater inflater = new Inflater();
		inflater.setInput( compressed );

		ByteArrayOutputStream bout = new ByteArrayOutputStream();

		try {
			byte[] comBuffer = new byte[1024];
			while ( !inflater.finished() ) {
				int read = inflater.inflate( comBuffer );
				bout.write( comBuffer, 0, read );
			}
		} catch ( DataFormatException e ) {
			System.out.println( "Failed to decompress batch packet" + e );
			return;
		}

		// More data please
		ByteBuffer byteBuffer = ByteBuffer.wrap( bout.toByteArray() );
		byteBuffer.order( ByteOrder.LITTLE_ENDIAN );
		byte[] stringBuffer = new byte[byteBuffer.getInt()];
		byteBuffer.get( stringBuffer );

		// Decode the json stuff (i really don't know why they base64 all of this)
		try {
			JSONObject jsonObject = (JSONObject) new JSONParser().parse( new String( stringBuffer ) );
			JSONArray chainArray = (JSONArray) jsonObject.get( "chain" );
			if ( chainArray != null ) {
				for ( Object chainObj : chainArray ) {
					JSONObject chainData = decodeBase64JSON( (String) chainObj );
					if ( chainData.containsKey( "extraData" ) ) {
						JSONObject extraData = (JSONObject) chainData.get( "extraData" );
						if ( extraData.containsKey( "displayName" ) ) {
							this.userName = (String) extraData.get( "displayName" );
						}

						if ( extraData.containsKey( "identity" ) ) {
							this.uuid = UUID.fromString( (String) extraData.get( "identity" ) );
						}
					}
				}
			}
		} catch ( ParseException e ) {
			e.printStackTrace();
		}

		// Skin comes next
		stringBuffer = new byte[byteBuffer.getInt()];
		byteBuffer.get( stringBuffer );
	}

	private JSONObject decodeBase64JSON( String data ) throws ParseException {
		String[] tempBase64 = data.split( "\\." );
		String payload = new String( Base64.getDecoder().decode( tempBase64[1] ) );
		return (JSONObject) new JSONParser().parse( payload );
	}

	/**
	 * Get the given Username for this Account
	 *
	 * @return UserName given by the client
     */
	public String getUserName() {
		return this.userName;
	}

	/**
	 * Get the given UUID for this Account
	 *
	 * @return UUID given by the client
	 */
	public UUID getUUID() {
		return this.uuid;
	}
}