/*
 * Copyright (c) 2016, GoMint, BlackyPaw and geNAZt
 *
 * This code is licensed under the BSD license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.gomint.proxprox.scheduler;

import io.gomint.proxprox.api.util.ExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Fabian
 * @version 1.0
 */
public class AsyncScheduledTask extends AbstractTask {

    private static final Logger LOGGER = LoggerFactory.getLogger( AsyncScheduledTask.class );
    private final Runnable task;

    private Future<?> future;

    private ExceptionHandler exceptionHandler;

    /**
     * Constructs a new AsyncScheduledTask. It needs to be executed via a normal {@link java.util.concurrent.ExecutorService}
     *
     * @param task runnable which should be executed
     */
    public AsyncScheduledTask( Runnable task ) {
        this.task = task;
    }

    @Override
    public void cancel() {
        this.future.cancel( true );
    }

    @Override
    public void onException( ExceptionHandler exceptionHandler ) {
        this.exceptionHandler = exceptionHandler;
    }

    @Override
    public void run() {
        // CHECKSTYLE:OFF
        try {
            this.task.run();
        } catch ( Exception e ) {
            if ( this.exceptionHandler != null ) {
                if ( !this.exceptionHandler.onException( e ) ) {
                    this.fireCompleteHandlers();
                    this.cancel();
                }
            } else {
                LOGGER.error( "No exception handler given", e );
            }
        }
        // CHECKSTYLE:ON
    }

    private void fireCompleteHandlers() {
        if ( this.handledHandler != null ) {
            this.handledHandler.onHandled();
        }
    }

    /**
     * Set the future of this task
     *
     * @param future of this task
     */
    void setFuture( Future<?> future ) {
        this.future = future;
    }

}