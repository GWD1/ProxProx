package io.gomint.proxprox.network.protocol.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

/**
 * @author geNAZt
 * @version 1.0
 */
@AllArgsConstructor
@Getter
public class PackIdVersion {

    private UUID id;
    private String version;

}
