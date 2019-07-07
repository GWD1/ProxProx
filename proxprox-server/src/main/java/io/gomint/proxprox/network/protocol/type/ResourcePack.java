package io.gomint.proxprox.network.protocol.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author geNAZt
 * @version 1.0
 */
@AllArgsConstructor
@Getter
public class ResourcePack {

    private PackIdVersion version;
    private long size;

}
