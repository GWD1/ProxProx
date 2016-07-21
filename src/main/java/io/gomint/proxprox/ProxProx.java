/*
 * Copyright (c) 2016, GoMint, BlackyPaw and geNAZt
 *
 * This code is licensed under the BSD license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.gomint.proxprox;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.gomint.jraknet.ServerSocket;
import io.gomint.proxprox.api.command.ConsoleCommandSender;
import io.gomint.proxprox.config.ProxyConfig;
import io.gomint.proxprox.network.SocketEventListener;
import io.gomint.proxprox.plugin.PluginManager;
import io.gomint.proxprox.scheduler.SyncTaskManager;
import io.gomint.proxprox.commands.*;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.SocketException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author geNAZt
 * @version 1.0
 */
public class ProxProx {

    private static final Logger logger = LoggerFactory.getLogger( ProxProx.class );
    private ProxyConfig config;

    // Thread pools
    private ThreadFactory clientConnectionsThreadFactory = new ThreadFactoryBuilder().setNameFormat( "UpStream-Connection-%d" ).build();
    private ThreadFactory serverConnectionsThreadFactory = new ThreadFactoryBuilder().setNameFormat( "DownStream-Connection-%d" ).build();

    // Task scheduling
    @Getter private ExecutorService executorService;
    @Getter private SyncTaskManager syncTaskManager;

    // Listener
    private ServerSocket serverSocket;
    private SocketEventListener socketEventListener;

    // Main thread
    private AtomicBoolean running = new AtomicBoolean( true );

    // Plugins
    @Getter private PluginManager pluginManager;

    /**
     * Entrypoint to ProxProx. This should be only called from the Bootstrap so we can
     * be sure we have all Libs loaded which we need.
     *
     * @param args optional arguments given via CLI arguments
     */
    public ProxProx( String[] args ) {
        logger.info( "Starting ProxProx v1.0.0" );

        // ------------------------------------ //
        // Executor Initialization
        // ------------------------------------ //
        ThreadFactory threadFactory = new ThreadFactory() {
            private AtomicLong counter = new AtomicLong( 0 );

            @Override
            public Thread newThread( Runnable r ) {
                Thread thread = Executors.defaultThreadFactory().newThread( r );
                thread.setName( "ProxProx Thread #" + counter.getAndIncrement() );
                return thread;
            }
        };

        this.executorService = new ThreadPoolExecutor( 0, 512, 60L, TimeUnit.SECONDS, new SynchronousQueue<>(), threadFactory );

        // We target 512 TPS
        long skipNanos = TimeUnit.SECONDS.toNanos( 1 ) / 512;
        this.syncTaskManager = new SyncTaskManager( this, skipNanos );

        // Load config first so we can override
        this.config = new ProxyConfig();

        try {
            this.config.initialize( new File( "config.cfg" ) );
        } catch ( IOException e ) {
            logger.error( "Could not init config.cfg. Please check for corruption.", e );
            System.exit( -1 );
        }

        // Parse optional arguments
        if ( !parseCommandLineArguments( args ) ) {
            System.exit( -1 );
        }

        // Load plugins
        File pluginDir = new File( "plugins/" );
        if ( !pluginDir.exists() ) {
            pluginDir.mkdirs();
        }

        this.pluginManager = new PluginManager( this, pluginDir );
        this.pluginManager.detectPlugins();
        this.pluginManager.loadPlugins();
        this.pluginManager.enablePlugins();

        // Register default commands
        this.pluginManager.registerCommand( null, new Commandstop( this ) );
        this.pluginManager.registerCommand( null, new Commandplugins( this.pluginManager ) );

        // Bind upstream UDP Raknet
        this.serverSocket = new ServerSocket( 10000 );
        this.serverSocket.setEventLoopFactory( new ThreadFactoryBuilder().setNameFormat( "jRaknet-Upstream-%d" ).build() );

        this.socketEventListener = new SocketEventListener( this );
        this.serverSocket.setEventHandler( this.socketEventListener );

        try {
            this.serverSocket.bind( this.config.getIp(), this.config.getPort() );
            logger.info( "Bound to " + this.config.getIp() + ":" + this.config.getPort() );
        } catch ( SocketException e ) {
            logger.error( "Failed to bind to " + this.config.getIp() + ":" + this.config.getPort(), e );
            System.exit( -1 );
        }

        // Read stdin
        new Thread( new Runnable() {
            @Override
            public void run() {
                Thread.currentThread().setName( "STDIN Read" );
                ConsoleCommandSender consoleCommandSender = new ConsoleCommandSender();

                BufferedReader in = new BufferedReader( new InputStreamReader( System.in ) );
                String s;
                try {
                    while ( running.get()  ) {
                        s = in.readLine();
                        if ( s != null && s.length() != 0 ) {
                            pluginManager.dispatchCommand( consoleCommandSender, s );
                        }
                    }
                } catch ( IOException e ) {
                    e.printStackTrace();
                }
            }
        } ).start();

        // Tick loop
        float lastTickTime = Float.MIN_NORMAL;
        Lock tickLock = new ReentrantLock();
        Condition tickCondition = tickLock.newCondition();

        while ( this.running.get() ) {
            tickLock.lock();
            try {
                long start = System.nanoTime();

                // Tick all major subsystems:
                long currentMillis = System.currentTimeMillis();
                this.syncTaskManager.update( currentMillis, lastTickTime );

                long diff = System.nanoTime() - start;
                lastTickTime = (float) diff / 1000000.0F;

                if ( diff < skipNanos ) {
                    tickCondition.await( skipNanos - diff, TimeUnit.NANOSECONDS );
                }
            } catch ( InterruptedException e ) {
                // Ignored ._.
            } finally {
                tickLock.unlock();
            }
        }
    }

    /**
     * Parses command line arguments and sets the respective fields of this class.
     *
     * @param args Command-Line arguments passed to the application
     * @return Returns true on success or false if any obligatory arguments are missing
     */
    private boolean parseCommandLineArguments( String[] args ) {
        for ( int i = 0; i < args.length; ++i ) {
            if ( args[i].startsWith( "--ip" ) ) {
                String[] split = args[i].split( "=" );
                if ( split.length == 2 ) {
                    this.config.setIp( split[1] );
                } else {
                    logger.error( "Malformed '--ip' command line option: Please specify actual IP value" );
                    return false;
                }
            } else if ( args[i].startsWith( "--port" ) ) {
                String[] split = args[i].split( "=" );
                if ( split.length == 2 ) {
                    try {
                        int port = Integer.valueOf( split[1] );
                        if ( port < 0 || port > 65535 ) {
                            throw new NumberFormatException();
                        }

                        this.config.setPort( port );
                    } catch ( NumberFormatException e ) {
                        logger.error( "Malformed '--port' command line option: Please specify valid integer port value" );
                        return false;
                    }
                } else {
                    logger.error( "Malformed '--port' command line option: Please specify actual IP value" );
                    return false;
                }
            } else {
                logger.error( "Unknown command line option '" + args[i] + "'" );
                return false;
            }
        }

        return true;
    }

    /**
     * Create a new thread for a UpStreamConnection
     *
     * @param runnable The runnable which should be executed
     * @return New Thread with the runnable loaded, ready to start
     */
    public Thread getNewClientConnectionThread( Runnable runnable ) {
        return clientConnectionsThreadFactory.newThread( runnable );
    }

    /**
     * Create a new thread for a DownStreamConnection
     *
     * @param runnable The runnable which should be executed
     * @return New Thread with the runnable loaded, ready to start
     */
    public Thread getNewServerConnectionThread( Runnable runnable ) {
        return serverConnectionsThreadFactory.newThread( runnable );
    }

    /**
     * Gracefully shutdown
     */
    public void shutdown() {
        logger.info( "Shutting down..." );

        // Close for new connections
        this.serverSocket.close();

        // First of all kick all players
        this.socketEventListener.disconnectAll( "Proxy shutting down" );

        // Shut down
        this.running.set( false );
    }

}
