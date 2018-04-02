/*
 * Copyright (c) 2017, GoMint, BlackyPaw and geNAZt
 *
 * This code is licensed under the BSD license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.gomint.proxprox.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author geNAZt
 * @version 1.0
 */
public class Watchdog {

    private static final Logger LOGGER = LoggerFactory.getLogger( Watchdog.class );
    private final Map<Long, Long> watchdogMap;
    private final Map<Long, Long> removed;

    public Watchdog( ExecutorService service, AtomicBoolean runningState ) {
        this.watchdogMap = new HashMap<>();
        this.removed = new HashMap<>();

        service.submit( new Runnable() {
            @Override
            public void run() {
                while ( runningState.get() ) {
                    check();

                    try {
                        Thread.sleep( 10 );
                    } catch ( InterruptedException e ) {
                        // Ignored .-.
                    }
                }
            }
        } );
    }

    private synchronized void check() {
        long currentTime = System.currentTimeMillis();

        Set<Long> removeSet = null;
        for ( Map.Entry<Long, Long> entry : this.watchdogMap.entrySet() ) {
            // Check if we are over endTime
            if ( currentTime > entry.getValue() ) {
                // Get the threads stack
                ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
                ThreadInfo threadInfo = threadMXBean.getThreadInfo( entry.getKey(), 10 );

                LOGGER.warn( "Thread did not work in time: {} (#{})", threadInfo.getThreadName(), threadInfo.getThreadId() );
                LOGGER.warn( "Status: {}", threadInfo.getThreadState() );
                for ( StackTraceElement element : threadInfo.getStackTrace() ) {
                    LOGGER.warn( "  {}", element );
                }

                if ( removeSet == null ) {
                    removeSet = new HashSet<>();
                }

                removeSet.add( entry.getKey() );
            }
        }

        if ( removeSet != null ) {
            for ( long threadId : removeSet ) {
                this.removed.put( threadId, this.watchdogMap.remove( threadId ) );
            }
        }
    }

    public synchronized void add( long diff, TimeUnit unit ) {
        long currentTime = System.currentTimeMillis();
        this.watchdogMap.put( Thread.currentThread().getId(), currentTime + unit.toMillis( diff ) );
    }

    public synchronized void done() {
        long threadId = Thread.currentThread().getId();
        this.watchdogMap.remove( threadId );

        if ( this.removed.containsKey( threadId ) ) {
            LOGGER.info( "Thread {} took {}ms", threadId, ( System.currentTimeMillis() - this.removed.remove( threadId ) ) );
        }
    }

}
