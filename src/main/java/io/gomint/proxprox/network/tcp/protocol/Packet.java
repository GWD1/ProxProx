package io.gomint.proxprox.network.tcp.protocol;

import io.netty.buffer.ByteBuf;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * @author geNAZt
 * @version 1.0
 */
@RequiredArgsConstructor
public abstract class Packet {

    @Getter
    private final byte id;

    public abstract void read( ByteBuf buf );
    public abstract void write( ByteBuf buf );

}
