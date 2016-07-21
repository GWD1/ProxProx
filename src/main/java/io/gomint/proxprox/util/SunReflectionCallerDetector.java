/*
 * Copyright (c) 2016, GoMint, BlackyPaw and geNAZt
 *
 * This code is licensed under the BSD license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.gomint.proxprox.util;

/**
 * @author geNAZt
 * @version 1.0
 */
public class SunReflectionCallerDetector implements CallerDetector {

    @Override
    public String getCallerClassName( int callDepth ) {
        return sun.reflect.Reflection.getCallerClass( callDepth ).getName();
    }

}
