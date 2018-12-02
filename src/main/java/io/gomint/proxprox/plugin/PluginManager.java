/*
 * Copyright (c) 2016, GoMint, BlackyPaw and geNAZt
 *
 * This code is licensed under the BSD license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.gomint.proxprox.plugin;

import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import io.gomint.proxprox.ProxProx;
import io.gomint.proxprox.api.command.Command;
import io.gomint.proxprox.api.command.CommandSender;
import io.gomint.proxprox.api.plugin.Plugin;
import io.gomint.proxprox.api.plugin.PluginMeta;
import io.gomint.proxprox.api.plugin.event.Event;
import io.gomint.proxprox.api.plugin.event.EventBus;
import io.gomint.proxprox.api.plugin.event.Listener;
import io.gomint.proxprox.scheduler.CoreScheduler;
import io.gomint.proxprox.scheduler.PluginScheduler;
import io.gomint.proxprox.util.CallerDetectorUtil;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

/**
 * @author geNAZt
 * @version 1.0
 */
public class PluginManager implements io.gomint.proxprox.api.plugin.PluginManager {

    private static final Logger logger = LoggerFactory.getLogger( PluginManager.class );
    private static final Pattern COMMAND_ARGS_SPLIT = Pattern.compile( " " );

    private final EventBus eventBus;
    private final Map<String, Plugin> plugins = new ConcurrentHashMap<>();
    private final Map<String, Command> commandMap = new HashMap<>();
    private final Map<String, Command> aliasMap = new HashMap<>();
    @Getter private final File container;
    private Map<String, PluginMeta> toLoad = new HashMap<>();
    private Map<String, Plugin> toEnable = new HashMap<>();
    private Multimap<Plugin, Command> commandsByPlugin = ArrayListMultimap.create();
    private Multimap<Plugin, Listener> listenersByPlugin = ArrayListMultimap.create();
    private PluginAutoDetector pluginAutoDetector;
    private final CoreScheduler scheduler;

    /**
     * Create a new Plugin Manager which loads and handled all Plugins
     *
     * @param proxy     The proxy for which we want to load plugins
     * @param container The folder which contains the Plugins
     */
    public PluginManager( ProxProx proxy, File container ) {
        this.scheduler = new CoreScheduler( proxy.getExecutorService(), proxy.getSyncTaskManager() );
        this.eventBus = new EventBus( null );
        this.pluginAutoDetector = new PluginAutoDetector();
        this.container = container;
    }

    /**
     * Register a command so that it may be executed.
     *
     * @param plugin  the plugin owning this command
     * @param command the command to register
     */
    public void registerCommand( Plugin plugin, Command command ) {
        commandMap.put( command.getName().toLowerCase(), command );

        for ( String alias : command.getAliases() ) {
            aliasMap.put( alias.toLowerCase(), command );
        }

        if ( plugin != null ) {
            commandsByPlugin.put( plugin, command );
        }
    }

    /**
     * Unregister a command so it will no longer be executed.
     *
     * @param command the command to unregister
     */
    public void unregisterCommand( Command command ) {
        commandMap.values().remove( command );
        aliasMap.values().remove( command );
        commandsByPlugin.values().remove( command );
    }

    /**
     * Unregister all command owned by a {@link Plugin}
     *
     * @param plugin the plugin to register the command of
     */
    public void unregisterCommands( Plugin plugin ) {
        for ( Iterator<Command> it = commandsByPlugin.get( plugin ).iterator(); it.hasNext(); ) {
            Command command = it.next();
            commandMap.values().remove( command );
            aliasMap.values().remove( command );
            it.remove();
        }
    }

    /**
     * Execute a command if it is registered, else return false.
     *
     * @param sender      The command sender which executed this command
     * @param commandLine the complete command line including command name and
     *                    arguments
     * @return whether the command was handled
     */
    public boolean dispatchCommand( CommandSender sender, String commandLine ) {
        String[] split = COMMAND_ARGS_SPLIT.split( commandLine );

        // Check for chat that only contains " "
        if ( split.length == 0 ) {
            return false;
        }

        String commandName = split[0].toLowerCase();
        Command command = commandMap.get( commandName );
        // Check for alias
        if ( command == null ) {
            command = aliasMap.get( commandName );
            if ( command == null ) {
                // Command not found
                return false;
            }
        }

        String[] args = Arrays.copyOfRange( split, 1, split.length );
        // CHECKSTYLE:OFF
        try {
            command.execute( sender, args );
        } catch ( Exception ex ) {
            sender.sendMessage( "§cAn internal error occurred whilst executing this command, please check the console log for details." );
            ex.printStackTrace();
        }
        // CHECKSTYLE:ON

        return true;
    }

    /**
     * Returns the {@link Plugin} objects corresponding to all loaded plugins.
     *
     * @return the set of loaded plugins
     */
    public Collection<Plugin> getPlugins() {
        return plugins.values();
    }

    /**
     * Returns a loaded plugin identified by the specified name.
     *
     * @param name of the plugin to retrieve
     * @return the retrieved plugin or null if not loaded
     */
    public Plugin getPlugin( String name ) {
        return plugins.containsKey( name ) ? plugins.get( name ) : toEnable.get( name );
    }

    /**
     * Load all plugins
     */
    public void loadPlugins() {
        Map<PluginMeta, Boolean> pluginStatuses = new HashMap<>();

        // Put in all currently loaded Plugins
        for ( Map.Entry<String, Plugin> namePluginEntry : plugins.entrySet() ) {
            pluginStatuses.put( namePluginEntry.getValue().getMeta(), true );
        }

        for ( Map.Entry<String, PluginMeta> entry : toLoad.entrySet() ) {
            PluginMeta plugin = entry.getValue();

            if ( !enablePlugin( pluginStatuses, new Stack<>(), plugin ) ) {
                logger.warn( "Failed to enable " + entry.getKey() );
            }
        }

        toLoad.clear();
        toLoad = null;
    }

    /**
     * Enable all Plugins
     */
    public void enablePlugins() {
        for ( Map.Entry<String, Plugin> namePluginEntry : toEnable.entrySet() ) {
            try {
                namePluginEntry.getValue().onInstall();
                logger.info( "Installed plugin {} version {}", namePluginEntry.getValue().getMeta().getName(), namePluginEntry.getValue().getMeta().getVersion() );
                plugins.put( namePluginEntry.getKey(), namePluginEntry.getValue() );
            } catch ( Throwable t ) {
                logger.warn( "Exception encountered when loading plugin: " + namePluginEntry.getValue().getMeta().getName(), t );
                disablePlugin( namePluginEntry.getValue() );
            }
        }

        toEnable.clear();
    }

    private boolean enablePlugin( Map<PluginMeta, Boolean> pluginStatuses, Stack<PluginMeta> dependStack, PluginMeta plugin ) {
        // Fast out when already loaded
        if ( pluginStatuses.containsKey( plugin ) ) {
            return pluginStatuses.get( plugin );
        }

        // combine all dependencies for 'for loop'
        Set<String> dependencies = new HashSet<>();
        if ( plugin.getDepends() != null ) {
            dependencies.addAll( plugin.getDepends() );
        }

        // success status
        boolean status = true;

        // try to load dependencies first
        for ( String dependName : dependencies ) {
            PluginMeta depend = toLoad.containsKey( dependName ) ? toLoad.get( dependName ) : ( plugins.containsKey( dependName ) ) ? plugins.get( dependName ).getMeta() : null;
            Boolean dependStatus = ( depend != null ) ? pluginStatuses.get( depend ) : Boolean.FALSE;

            if ( dependStatus == null ) {
                if ( dependStack.contains( depend ) ) {
                    StringBuilder dependencyGraph = new StringBuilder();

                    for ( PluginMeta element : dependStack ) {
                        dependencyGraph.append( element.getName() ).append( " -> " );
                    }

                    dependencyGraph.append( plugin.getName() ).append( " -> " ).append( dependName );

                    logger.warn( "Circular dependency detected: " + dependencyGraph );
                    status = false;
                } else {
                    dependStack.push( plugin );
                    dependStatus = this.enablePlugin( pluginStatuses, dependStack, depend );
                    dependStack.pop();
                }
            }

            if ( dependStatus == Boolean.FALSE ) {
                logger.warn( "%s (required by %s) is unavailable", String.valueOf( dependName ), plugin.getName() );
                status = false;
            }

            if ( !status ) {
                break;
            }
        }

        // do actual loading
        if ( status ) {
            try {
                URLClassLoader loader = new PluginClassLoader( new URL[]{
                        plugin.getPluginFile().toURI().toURL()
                } );
                Class<?> main = loader.loadClass( plugin.getMainClass() );
                Plugin clazz = (Plugin) main.getConstructor().newInstance();

                // TODO: Secure this
                clazz.setMeta( plugin );
                clazz.setPluginManager( this );
                clazz.setLogger( LoggerFactory.getLogger( main ) );
                clazz.setScheduler( new PluginScheduler( clazz, this.scheduler ) );

                toEnable.put( plugin.getName(), clazz );
                clazz.onStartup();

                logger.info( "Loaded plugin {} version {}", plugin.getName(), plugin.getVersion() );
            } catch ( Throwable t ) {
                logger.warn( "Error enabling plugin " + plugin.getName(), t );
            }
        }

        pluginStatuses.put( plugin, status );
        return status;
    }

    /**
     * Load all plugins from the specified folder.
     */
    public void detectPlugins() {
        Preconditions.checkNotNull( container, "folder" );
        Preconditions.checkArgument( container.isDirectory(), "Must load from a directory" );

        if ( toLoad == null ) {
            toLoad = new HashMap<>();
        }

        File[] filesToLoad = container.listFiles();
        if ( filesToLoad == null ) {
            return;
        }

        for ( File file : filesToLoad ) {
            if ( file.isFile() && file.getName().endsWith( ".jar" ) ) {
                try ( JarFile jar = new JarFile( file ) ) {
                    PluginMeta desc = pluginAutoDetector.checkPlugin( jar );
                    Preconditions.checkNotNull( desc, "Plugin could not be autodetected" );

                    if ( !plugins.containsKey( desc.getName() ) ) {
                        desc.setPluginFile( file );
                        toLoad.put( desc.getName(), desc );
                    }
                } catch ( Throwable ex ) {
                    logger.warn( "Could not load plugin from file " + file, ex );
                }
            }
        }
    }

    /**
     * Dispatch an event to all subscribed listeners and return the event once
     * it has been handled by these listeners.
     *
     * @param event the event to call
     * @param <T>   Type of event
     * @return Future of the called event
     */
    public <T extends Event> T callEvent( T event ) {
        Preconditions.checkNotNull( event, "event" );
        return this.eventBus.post( event );
    }

    /**
     * Register a {@link Listener} for receiving called events. Methods in this
     * Object which wish to receive events must be annotated with the
     * {@link io.gomint.proxprox.api.plugin.event.EventHandler} annotation.
     *
     * @param plugin   the owning plugin
     * @param listener the listener to register events for
     */
    public void registerListener( Plugin plugin, Listener listener ) {
        eventBus.register( listener );
        listenersByPlugin.put( plugin, listener );
    }

    /**
     * Unregister a {@link Listener} so that the events do not reach it anymore.
     *
     * @param listener the listener to unregister
     */
    public void unregisterListener( Listener listener ) {
        eventBus.unregister( listener );
        listenersByPlugin.values().remove( listener );
    }

    /**
     * Unregister all of a Plugin's listener.
     *
     * @param plugin The plugin for which all listeners should be removed
     */
    public void unregisterListeners( Plugin plugin ) {
        for ( Iterator<Listener> it = listenersByPlugin.get( plugin ).iterator(); it.hasNext(); ) {
            eventBus.unregister( it.next() );
            it.remove();
        }
    }

    /**
     * Disable a specific given Plugin
     *
     * @param plugin The plugin which should be disabled
     */
    public void disablePlugin( Plugin plugin ) {
        // Check for security
        if ( !CallerDetectorUtil.getCallerClassName( 2 ).equals( plugin.getClass().getName() ) ) {
            throw new SecurityException( "Plugins can only disable themselves" );
        }

        this.disablePlugin0(plugin);
    }

    /**
     * Disable a specific given plugin. Bypasses the security check
     * by {@see disablePlugin}
     *
     * @param plugin The plugin which should be disabled
     */
    private void disablePlugin0( Plugin plugin ) {
        // Check if this plugin is still enabled
        if ( !getPlugins().contains( plugin ) ) {
            return;
        }

        // Check which Plugins also need to be disabled
        for ( Plugin plugin1 : new ArrayList<>( getPlugins() ) ) {
            if ( !plugin1.equals( plugin ) && plugin1.getMeta().getDepends().contains( plugin.getMeta().getName() ) ) {
                disablePlugin( plugin1 );
            }
        }

        // Tell the Plugin we disable
        logger.info( "Disabling plugin " + plugin.getMeta().getName() );

        // CHECKSTYLE:OFF
        try {
            plugin.onUninstall();
        } catch ( Exception e ) {
            logger.warn( "Error while disabling " + plugin.getMeta().getName(), e );
        }
        // CHECKSTYLE:ON

        // Disable this
        unregisterCommands( plugin );
        unregisterListeners( plugin );
        ( ( PluginScheduler ) plugin.getScheduler() ).cleanup();
        ( (PluginClassLoader) plugin.getClass().getClassLoader() ).remove();

        // Remove from Pluginslist
        plugins.remove( plugin.getMeta().getName() );
    }

    @Override
    public String getBaseDirectory() {
        return this.container.getAbsolutePath();
    }

    /**
     * Get a collection of currently loaded plugins
     *
     * @return collection of loaded plugins
     */
    public Collection<Command> getCommands() {
        return commandMap.values();
    }

    /**
     * Disable all currently loaded plugins
     */
    public void shutdown() {
        for ( Plugin plugin : this.getPlugins() ) {
            // Bypasses the security check
            this.disablePlugin0( plugin );
        }
    }

}
