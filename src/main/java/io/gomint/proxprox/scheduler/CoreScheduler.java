/*
 * Copyright (c) 2016, GoMint, BlackyPaw and geNAZt
 *
 * This code is licensed under the BSD license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.gomint.proxprox.scheduler;

import io.gomint.proxprox.api.scheduler.Scheduler;
import lombok.RequiredArgsConstructor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author Fabian
 * @version 1.0
 */
@RequiredArgsConstructor
public class CoreScheduler implements Scheduler {

    private final ScheduledExecutorService executorService;
    private final SyncTaskManager syncTaskManager;

    @Override
    public AbstractTask executeAsync( Runnable runnable ) {
        return this.scheduleAsync( runnable, 0, TimeUnit.MILLISECONDS );
    }

    @Override
    public AbstractTask scheduleAsync( Runnable runnable, long delay, TimeUnit timeUnit ) {
        return this.scheduleAsync( runnable, delay, -1, timeUnit );
    }

    @Override
    public AbstractTask scheduleAsync( Runnable runnable, long delay, long period, TimeUnit timeUnit ) {
        AsyncScheduledTask task = new AsyncScheduledTask( runnable );

        Future<?> future;
        if ( period > 0 ) {
            future = this.executorService.scheduleAtFixedRate( task, delay, period, timeUnit );
        } else if ( delay > 0 ) {
            future = this.executorService.schedule( task, delay, timeUnit );
        } else {
            future = this.executorService.submit( task );
        }

        task.setFuture( future );
        return task;
    }

    @Override
    public AbstractTask executeSync( Runnable runnable ) {
        return this.scheduleSync( runnable, 0, TimeUnit.MILLISECONDS );
    }

    @Override
    public AbstractTask scheduleSync( Runnable runnable, long delay, TimeUnit timeUnit ) {
        return this.scheduleSync( runnable, delay, -1, timeUnit );
    }

    @Override
    public AbstractTask scheduleSync( Runnable runnable, long delay, long period, TimeUnit timeUnit ) {
        SyncScheduledTask task = new SyncScheduledTask( runnable, delay, period, timeUnit );
        syncTaskManager.addTask( task );
        return task;
    }

}
