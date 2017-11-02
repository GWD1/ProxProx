package io.gomint.proxprox.network.protocol;

import io.gomint.jraknet.PacketBuffer;
import io.gomint.proxprox.api.network.Packet;
import io.gomint.proxprox.network.Protocol;

public class PacketEncryptionReady extends Packet {

    public PacketEncryptionReady() {
		super( Protocol.PACKET_ENCRYPTION_READY );
	}
	
	@Override
	public void serialize( PacketBuffer buffer ) {
		
	}
	
	@Override
	public void deserialize( PacketBuffer buffer ) {
		
	}
	
}