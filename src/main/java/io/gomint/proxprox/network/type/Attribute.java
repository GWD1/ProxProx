package io.gomint.proxprox.network.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * @author geNAZt
 * @version 1.0
 */
@RequiredArgsConstructor
@Getter
public class Attribute {

    private final String key;
    private final float minValue;
    private final float value;
    private final float maxValue;

}
