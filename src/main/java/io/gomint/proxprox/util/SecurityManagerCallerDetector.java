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
public class SecurityManagerCallerDetector implements CallerDetector {

    private static final MySecurityManager SEC_MANAGER = new MySecurityManager();

    /**
     * Get the name of the class which called this method
     *
     * @param callDepth depth at which we want to look
     * @return String of class which called
     */
    public String getCallerClassName( int callDepth ) {
        return SEC_MANAGER.getCallerClassName( callDepth );
    }

    /**
     * A custom security manager that exposes the getClassContext() information
     */
    private static class MySecurityManager extends SecurityManager {

        /**
         * Get the name of the class which called this method
         *
         * @param callStackDepth depth at which we want to look
         * @return String of class which called
         */
        String getCallerClassName( int callStackDepth ) {
            return getClassContext()[callStackDepth].getName();
        }

    }

}
