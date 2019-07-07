/*
 * Copyright (c) 2017, GoMint, BlackyPaw and geNAZt
 *
 * This code is licensed under the BSD license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.gomint.proxprox.network;

import io.gomint.jraknet.PacketBuffer;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author geNAZt
 * @version 1.0
 */
@EqualsAndHashCode
class PostProcessExecutor implements Runnable {

    @Getter
    private final AtomicInteger connectionsInUse = new AtomicInteger( 0 );
    private Queue<PostProcessWorker> workers = new ConcurrentLinkedQueue<>();
    @Getter
    private float load;
    private Future<?> future;
    private AtomicBoolean running = new AtomicBoolean( true );
    private final Object waiter = new Object();
    private final ExecutorService executorService;

    public PostProcessExecutor( ExecutorService executorService ) {
        this.executorService = executorService;
        this.future = executorService.submit( this );
    }

    public void addWork( AbstractConnection connection, List<PacketBuffer> packets ) {
        this.workers.offer( new PostProcessWorker( connection, packets ) );

        synchronized ( this.waiter ) {
            this.waiter.notifyAll();
        }
    }

    public void stop() {
        this.running.set( false );
        this.future.cancel( true );
    }

    @Override
    public void run() {
        while ( this.running.get() && !this.executorService.isShutdown() ) {
            long start = System.currentTimeMillis();

            while ( !this.workers.isEmpty() ) {
                PostProcessWorker worker = this.workers.poll();
                if ( worker != null ) {
                    worker.run();
                }
            }

            this.load = ( ( System.currentTimeMillis() - start ) / 50f ) * 100;

            // Wait on the next worker
            if ( this.workers.isEmpty() ) {
                synchronized ( this.waiter ) {
                    try {
                        this.waiter.wait( 500 );
                    } catch ( InterruptedException e ) {
                        // Ignored
                    }
                }
            }
        }
    }

}
