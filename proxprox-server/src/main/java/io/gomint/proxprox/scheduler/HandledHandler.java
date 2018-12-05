/*
 * Copyright (c) 2016, GoMint, BlackyPaw and geNAZt
 *
 * This code is licensed under the BSD license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.gomint.proxprox.scheduler;

/**
 * @author geNAZt
 * @version 1.0
 */
interface HandledHandler {

    /**
     * Gets called when the task has been handled and will be executed no more
     */
    void onHandled();

}
