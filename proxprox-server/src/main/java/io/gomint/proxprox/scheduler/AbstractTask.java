/*
 * Copyright (c) 2016, GoMint, BlackyPaw and geNAZt
 *
 * This code is licensed under the BSD license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.gomint.proxprox.scheduler;

import io.gomint.proxprox.api.scheduler.Task;

/**
 * @author geNAZt
 * @version 1.0
 */
public abstract class AbstractTask implements Task, Runnable {

    /**
     * Instance of a handled handler to let the origin know the task has been handled
     */
    protected HandledHandler handledHandler;

    /**
     * Set the handled handler so we can call back when we are done
     *
     * @param handler The new HandledHandler which we use to call back
     */
    void onHandled( HandledHandler handler ) {
        this.handledHandler = handler;
    }

}
