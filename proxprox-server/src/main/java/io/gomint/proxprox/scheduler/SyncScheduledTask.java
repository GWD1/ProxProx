/*
 * Copyright (c) 2016, GoMint, BlackyPaw and geNAZt
 *
 * This code is licensed under the BSD license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.gomint.proxprox.scheduler;

import io.gomint.proxprox.api.util.ExceptionHandler;
import lombok.Getter;

import java.util.concurrent.TimeUnit;

/**
 * @author geNAZt
 * @version 1.0
 */
public class SyncScheduledTask extends AbstractTask {
    private final Runnable task;
    private final long period;          // -1 means no reschedule
    @Getter private long nextExecution; // -1 is cancelled
    private ExceptionHandler exceptionHandler;

    /**
     * Constructs a new SyncScheduledTask. It needs to be executed via a normal {@link java.util.concurrent.ExecutorService}
     *
     * @param task              The runnable which should be executed
     * @param delay             Amount of time units to wait until the invocation of this execution
     * @param period            Amount of time units for the delay after execution to run the runnable again
     * @param unit of time
     */
    public SyncScheduledTask( Runnable task, long delay, long period, TimeUnit unit ) {
        this.task = task;
        this.period = ( period >= 0) ? unit.toMillis( period ) : -1;
        this.nextExecution = ( delay >= 0 ) ? System.currentTimeMillis() + unit.toMillis( delay ) : -1;
    }

    @Override
    public void run() {
        // CHECKSTYLE:OFF
        try {
            this.task.run();
        } catch ( Exception e ) {
            if ( this.exceptionHandler != null ) {
                if ( !this.exceptionHandler.onException( e ) ) {
                    this.cancel();
                }
            } else {
                e.printStackTrace();
            }
        }
        // CHECKSTYLE:ON

        if ( this.period > 0 ) {
            this.nextExecution = System.currentTimeMillis() + this.period;
        } else {
            this.cancel();
        }
    }

    @Override
    public void cancel() {
        if ( this.handledHandler != null ) {
            this.handledHandler.onHandled();
        }

        this.nextExecution = -1;
    }

    @Override
    public void onException( ExceptionHandler exceptionHandler ) {
        this.exceptionHandler = exceptionHandler;
    }

}
