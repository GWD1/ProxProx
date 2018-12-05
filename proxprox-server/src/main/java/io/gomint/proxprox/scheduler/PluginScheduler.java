/*
 * Copyright (c) 2016, GoMint, BlackyPaw and geNAZt
 *
 * This code is licensed under the BSD license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.gomint.proxprox.scheduler;


import io.gomint.proxprox.api.plugin.Plugin;
import io.gomint.proxprox.api.scheduler.Scheduler;
import io.gomint.proxprox.api.scheduler.Task;
import io.gomint.proxprox.api.util.ExceptionHandler;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author geNAZt
 * @version 1.0
 */
public class PluginScheduler implements Scheduler {

    private Plugin plugin;
    private CoreScheduler coreScheduler;

    private Set<Task> runningTasks = new HashSet<>();
    private ReentrantLock lock = new ReentrantLock( true );

    /**
     * Create a new Plugin Scheduler
     *
     * @param plugin    The plugin for which we schedule
     * @param scheduler The core scheduler to use
     */
    public PluginScheduler( Plugin plugin, CoreScheduler scheduler ) {
        this.plugin = plugin;
        this.coreScheduler = scheduler;
    }

    @Override
    public Task executeAsync( Runnable runnable ) {
        if ( this.coreScheduler == null ) {
            throw new IllegalStateException( "This PluginScheduler has been cleaned and closed. No new Tasks can be scheduled" );
        }

        lock.lock();

        try {
            AbstractTask task = coreScheduler.executeAsync( runnable );
            task.onException( new ExceptionHandler() {
                @Override
                public boolean onException( Exception e ) {
                    plugin.getLogger().warn( "A task thrown a Exception", e );
                    return true;
                }
            } );

            this.runningTasks.add( task );

            task.onHandled( new HandledHandler() {
                @Override
                public void onHandled() {
                    lock.lock();
                    try {
                        PluginScheduler.this.runningTasks.remove( task );
                    } finally {
                        lock.unlock();
                    }
                }
            } );

            return task;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Task scheduleAsync( Runnable runnable, long delay, TimeUnit timeUnit ) {
        if ( this.coreScheduler == null ) {
            throw new IllegalStateException( "This PluginScheduler has been cleaned and closed. No new Tasks can be scheduled" );
        }

        lock.lock();

        try {
            AbstractTask task = coreScheduler.scheduleAsync( runnable, delay, timeUnit );
            task.onException( new ExceptionHandler() {
                @Override
                public boolean onException( Exception e ) {
                    plugin.getLogger().warn( "A task thrown a Exception", e );
                    return true;
                }
            } );

            this.runningTasks.add( task );

            task.onHandled( new HandledHandler() {
                @Override
                public void onHandled() {
                    lock.lock();
                    try {
                        PluginScheduler.this.runningTasks.remove( task );
                    } finally {
                        lock.unlock();
                    }
                }
            } );

            return task;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Task scheduleAsync( Runnable runnable, long delay, long period, TimeUnit timeUnit ) {
        if ( this.coreScheduler == null ) {
            throw new IllegalStateException( "This PluginScheduler has been cleaned and closed. No new Tasks can be scheduled" );
        }

        lock.lock();

        try {
            AbstractTask task = coreScheduler.scheduleAsync( runnable, delay, period, timeUnit );
            task.onException( new ExceptionHandler() {
                @Override
                public boolean onException( Exception e ) {
                    plugin.getLogger().warn( "A task thrown a Exception", e );
                    return true;
                }
            } );

            this.runningTasks.add( task );

            task.onHandled( new HandledHandler() {
                @Override
                public void onHandled() {
                    lock.lock();
                    try {
                        PluginScheduler.this.runningTasks.remove( task );
                    } finally {
                        lock.unlock();
                    }
                }
            } );

            return task;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Task executeSync( Runnable runnable ) {
        if ( this.coreScheduler == null ) {
            throw new IllegalStateException( "This PluginScheduler has been cleaned and closed. No new Tasks can be scheduled" );
        }

        lock.lock();

        try {
            AbstractTask task = coreScheduler.executeSync( runnable );
            task.onException( new ExceptionHandler() {
                @Override
                public boolean onException( Exception e ) {
                    plugin.getLogger().warn( "A task thrown a Exception", e );
                    return true;
                }
            } );

            this.runningTasks.add( task );

            task.onHandled( new HandledHandler() {
                @Override
                public void onHandled() {
                    lock.lock();
                    try {
                        PluginScheduler.this.runningTasks.remove( task );
                    } finally {
                        lock.unlock();
                    }
                }
            } );

            return task;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Task scheduleSync( Runnable runnable, long delay, TimeUnit timeUnit ) {
        if ( this.coreScheduler == null ) {
            throw new IllegalStateException( "This PluginScheduler has been cleaned and closed. No new Tasks can be scheduled" );
        }

        lock.lock();

        try {
            AbstractTask task = coreScheduler.scheduleSync( runnable, delay, timeUnit );
            task.onException( new ExceptionHandler() {
                @Override
                public boolean onException( Exception e ) {
                    plugin.getLogger().warn( "A task thrown a Exception", e );
                    return true;
                }
            } );

            this.runningTasks.add( task );

            task.onHandled( new HandledHandler() {
                @Override
                public void onHandled() {
                    lock.lock();
                    try {
                        PluginScheduler.this.runningTasks.remove( task );
                    } finally {
                        lock.unlock();
                    }
                }
            } );

            return task;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Task scheduleSync( Runnable runnable, long delay, long period, TimeUnit timeUnit ) {
        if ( this.coreScheduler == null ) {
            throw new IllegalStateException( "This PluginScheduler has been cleaned and closed. No new Tasks can be scheduled" );
        }

        lock.lock();

        try {
            AbstractTask task = coreScheduler.scheduleSync( runnable, delay, period, timeUnit );
            task.onException( new ExceptionHandler() {
                @Override
                public boolean onException( Exception e ) {
                    plugin.getLogger().warn( "A task thrown a Exception", e );
                    return true;
                }
            } );

            this.runningTasks.add( task );

            task.onHandled( new HandledHandler() {
                @Override
                public void onHandled() {
                    lock.lock();
                    try {
                        PluginScheduler.this.runningTasks.remove( task );
                    } finally {
                        lock.unlock();
                    }
                }
            } );

            return task;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Internal Method for cleaning up all Tasks
     */
    public void cleanup() {
        for ( Task runningTask : this.runningTasks ) {
            runningTask.cancel();
        }

        this.runningTasks.clear();
        this.plugin = null;
        this.coreScheduler = null;
    }

}
