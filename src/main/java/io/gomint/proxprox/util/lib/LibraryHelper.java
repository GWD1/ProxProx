package io.gomint.proxprox.util.lib;

import io.gomint.proxprox.Bootstrap;
import io.gomint.proxprox.util.lib.handler.DeleteAllLibraryActionHandler;
import io.gomint.proxprox.util.lib.handler.DeleteLibraryActionHandler;
import io.gomint.proxprox.util.lib.handler.DownloadLibraryActionHandler;
import io.gomint.proxprox.util.lib.handler.LibraryActionHandler;
import org.apache.logging.log4j.core.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Providing functionality to fetch libraries and storing them locally.
 * {@code LibraryHelper} makes use of {@link LibraryActionHandler} to handle different variations
 * of commands / actions. Currently the {@code LibraryHelper} comes with these action handler
 * standard implementations:
 * <ul>
 * <li>Download: Downloads a library by using the given URL ~ {@link DownloadLibraryActionHandler}</li>
 * <li>Delete: Deletes a library ~ {@link DeleteLibraryActionHandler}</li>
 * <li>DeleteAll: Deletes all saved libraries ~ {@link DeleteAllLibraryActionHandler}</li>
 * </ul>
 *
 * @author Shad0wCore
 * @version 1.0.3
 */
public final class LibraryHelper {

    private static class ActionRegistry {
        /* Registered handlers */
        private static final Map<String, LibraryActionHandler> ACTION_HANDLERS = new HashMap<>();
        private static final Logger LOGGER = LoggerFactory.getLogger( ActionRegistry.class );

        // ====================================== DEFAULT HANDLERS ====================================== //
        static {
            assignHandler( "download", DownloadLibraryActionHandler.class );
            assignHandler( "delete", DeleteLibraryActionHandler.class );
            assignHandler( "deleteAll", DeleteAllLibraryActionHandler.class );
        }

        /* Only protected access granted as developers won't be able to call this method before plugins have been initialized */
        static void assignHandler( String action, Class<? extends LibraryActionHandler> handlerClass ) {
            if ( action == null ) {
                LOGGER.warn( "Failed assigning handler: 'action' is referring to null" );
                return;
            }
            if ( handlerClass == null ) {
                LOGGER.warn( "Failed assigning handler: 'handlerClass' is referring to null" );
                return;
            }

            /* Avoid having pointless exceptions when instantiating a handler */
            if ( handlerClass.isInterface() || Modifier.isAbstract( handlerClass.getModifiers() ) ) {
                LOGGER.warn( "Failed assigning handler: Class cannot be an interface nor can it be abstract" );
                return;
            }

            LibraryActionHandler libraryActionHandler;
            try {
                Constructor constructor = handlerClass.getDeclaredConstructor();
                constructor.setAccessible( true );

                libraryActionHandler = (LibraryActionHandler) constructor.newInstance();
            } catch ( Exception e ) {
                LOGGER.error( "Encountered errors while instantiating action handler:", e );
                return;
            }

            ACTION_HANDLERS.put( action, libraryActionHandler );
            LOGGER.debug( "Assignment was made: '" + action + "' <- " + handlerClass.getName() );
        }

        static LibraryActionHandler getHandler( String action ) {
            if ( action == null ) {
                LOGGER.debug( "Failed gathering handler: 'action' is referring to null" );
                return null;
            }

            return ACTION_HANDLERS.get( action );
        }

    }

    public static final File LIBRARY_DIRECTORY = new File( "libs/" );
    public static final Logger LOGGER = LoggerFactory.getLogger( LibraryHelper.class );

    /**
     * Resolves all libraries which have been registered in the libs.dep file.
     * The method accesses the internal libs.dep located in the root directory of the Jar.
     * <p>
     * How a libs.dep entry should look like: [action]~(jar-file-url)
     * Example: download~https://dl.gomint.io/bcprov-jdk15on-1.59.jar
     */
    public void checkDepFile() {
        readDepEntries( getBufferedReaderOfDepFile() );
    }

    /**
     * Resolves all libraries which have been registered in the libs.dep file.
     * The method accesses the given dep file.
     * <p>
     * How a libs.dep entry should look like: [action]~(jar-file-url)
     * Example: download~https://dl.gomint.io/bcprov-jdk15on-1.59.jar
     *
     * @param depFile Dep file containing libraries which should be resolved
     */
    public void checkDepFile( File depFile ) {
        if ( !depFile.exists() ) {
            return;
        }
        if ( !"dep".equalsIgnoreCase( FileUtils.getFileExtension( depFile ) ) ) {
            return;
        }

        try {
            readDepEntries( new BufferedReader( new FileReader( depFile ) ) );
        } catch ( FileNotFoundException e ) {
            LOGGER.error( "Failed reading dep file entries: ", e );
        }
    }

    /**
     * Dispatch an action by its name
     *
     * @param action Action's label
     * @param args   Pass arguments the handler can make use of
     */
    public void dispatchAction( String action, String... args ) {
        LibraryActionHandler handler = ActionRegistry.getHandler( action );
        if ( handler == null ) {
            LOGGER.warn( "Could not dispatch action: No handler available for '" + action + "'" );
            return;
        }

        handler.handleAction( action, LIBRARY_DIRECTORY, args );
    }

    private void readDepEntries( BufferedReader reader ) {
        // Check if we are able to skip this
        if ( System.getProperty( "skip.libCheck", "false" ).equals( "true" ) ) {
            return;
        }

        try {
            String line;
            while ( ( line = reader.readLine() ) != null ) {
                // Check for comment, if commented skip action
                if ( line.isEmpty() || line.equals( System.getProperty( "line.separator" ) ) || line.startsWith( "#" ) ) {
                    continue;
                }

                String[] actionCompounds = line.split( "~" );
                String[] handlerArgs = Arrays.copyOfRange( actionCompounds, 1, actionCompounds.length );

                dispatchAction( actionCompounds[0], handlerArgs );
            }
        } catch ( IOException e ) {
            LOGGER.error( "Failed checking dependency file: ", e );
        } finally {
            try {
                reader.close();
            } catch ( IOException e ) {
                LOGGER.error( "Failed closing reader: ", e );
            }
        }
    }

    private BufferedReader getBufferedReaderOfDepFile() {
        return new BufferedReader( new InputStreamReader( Bootstrap.class.getResourceAsStream( "/libs.dep" ) ) );
    }

}
