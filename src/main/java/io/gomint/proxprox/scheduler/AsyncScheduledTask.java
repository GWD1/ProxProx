/*
 * Copyright (c) 2016, GoMint, BlackyPaw and geNAZt
 *
 * This code is licensed under the BSD license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.gomint.proxprox.scheduler;

import io.gomint.proxprox.api.util.ExceptionHandler;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Fabian
 * @version 1.0
 */
public class AsyncScheduledTask extends AbstractTask {
    private final Runnable task;

    private final long delay;   // -1 means no execution
    private final long period;  // <= 0 means no reschedule
    private final AtomicBoolean running = new AtomicBoolean( true );

    private ExceptionHandler exceptionHandler;
    private Thread executingThread;

    /**
     * Constructs a new AsyncScheduledTask. It needs to be executed via a normal {@link java.util.concurrent.ExecutorService}
     *
     * @param task runnable which should be executed
     * @param delay of this execution
     * @param period delay after execution to run the runnable again
     * @param unit of time
     */
    public AsyncScheduledTask( Runnable task, long delay, long period, TimeUnit unit ) {
        this.task = task;
        this.delay = (delay >= 0) ? unit.toMillis( delay ) : -1;
        this.period = (period >= 0 ) ? unit.toMillis( period ) : -1;
    }

    @Override
    public void cancel() {
        this.running.set( false );
        this.executingThread.interrupt();
    }

    @Override
    public void onException( ExceptionHandler exceptionHandler ) {
        this.exceptionHandler = exceptionHandler;
    }

    @Override
    public void run() {
        // Fast path to failout
        if ( this.delay == -1 ) {
            if ( this.handledHandler != null ) {
                this.handledHandler.onHandled();
            }

            return;
        }

        this.executingThread = Thread.currentThread();

        // Check if we need to wait for the first execution
        if ( this.delay > 0 ) {
            try {
                Thread.sleep( this.delay );
            } catch ( InterruptedException ex ) {
                this.executingThread.interrupt();
            }
        }

        while ( this.running.get() ) {
            // CHECKSTYLE:OFF
            try {
                this.task.run();
            } catch ( Exception e ) {
                if ( this.exceptionHandler != null ) {
                    if ( !this.exceptionHandler.onException( e ) ) {
                        if ( this.handledHandler != null ) {
                            this.handledHandler.onHandled();
                        }

                        return;
                    }
                } else {
                    e.printStackTrace();
                }
            }
            // CHECKSTYLE:ON

            // If we have a period of 0 or less, only run once
            if ( this.period <= 0 ) {
                if ( this.handledHandler != null ) {
                    this.handledHandler.onHandled();
                }

                break;
            }

            try {
                Thread.sleep( this.period );
            } catch ( InterruptedException ex ) {
                this.executingThread.interrupt();
            }
        }
    }

}