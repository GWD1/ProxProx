/*
 * Copyright (c) 2016, GoMint, BlackyPaw and geNAZt
 *
 * This code is licensed under the BSD license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.gomint.proxprox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

/**
 * This Bootstrap downloads all Libraries given inside of the "libs.dep" File in the Root
 * of the Application Workdir and then instanciates the Class which is given as Application
 * entry point.
 *
 * @author geNAZt
 * @version 1.0
 */
public class Bootstrap {

    private static final Logger LOGGER = LoggerFactory.getLogger( Bootstrap.class );

    /**
     * Main entry point. May be used for custom dependency injection, dynamic
     * library class loaders and other experiments which need to be done before
     * the actual main entry point is executed.
     *
     * @param args The command-line arguments to be passed to the entryClass
     */
    public static void main( String[] args ) {
        // Check if classloader has been changed (it should be a URLClassLoader)
        if ( !( ClassLoader.getSystemClassLoader() instanceof URLClassLoader ) ) {
            System.out.println( "System Classloader is no URLClassloader" );
            System.exit( -1 );
        }

        // Check if we need to create the libs Folder
        File libsFolder = new File( "libs/" );
        if ( !libsFolder.exists() && !libsFolder.mkdirs() ) {
            System.out.println( "Could not create library Directory" );
            System.exit( -1 );
        }

        // Check the libs (versions and artifacts)
        checkLibs( libsFolder );

        File[] files = libsFolder.listFiles();
        if ( files == null ) {
            System.out.println( "Library Directory is corrupted" );
            System.exit( -1 );
        }

        // Scan the libs/ Directory for .jar Files
        for ( File file : files ) {
            if ( file.getAbsolutePath().endsWith( ".jar" ) ) {
                try {
                    LOGGER.info( "Loading lib: " + file.getAbsolutePath() );
                    addJARToClasspath( file );
                } catch ( IOException e ) {
                    e.printStackTrace();
                }
            }
        }

        // Load the Class entrypoint
        try {
            Class<?> coreClass = ClassLoader.getSystemClassLoader().loadClass( "io.gomint.proxprox.ProxProx" );
            Constructor constructor = coreClass.getDeclaredConstructor( String[].class );
            constructor.newInstance( new Object[]{args} );
        } catch ( ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException | InstantiationException e ) {
            e.printStackTrace();
        }
    }

    /**
     * Download needed Libs from the central Maven repository or any other Repo (can be any url in the libs.dep file)
     *
     * @param libsFolder in which the downloads should be stored
     */
    private static void checkLibs( File libsFolder ) {
        // Check if we are able to skip this
        if ( System.getProperty( "skip.libcheck", "false" ).equals( "true" ) ) {
            return;
        }

        // Load the dependency list
        try ( BufferedReader reader = new BufferedReader( new InputStreamReader( Bootstrap.class.getResourceAsStream( "/libs.dep" ) ) ) ) {
            // Parse the line
            String line;
            while ( ( line = reader.readLine() ) != null ) {
                // Check for comment
                if ( line.isEmpty() || line.equals( System.getProperty( "line.separator" ) ) || line.startsWith( "#" ) ) {
                    continue;
                }

                // Extract the command mode
                String[] splitCommand = line.split( "~" );
                switch ( splitCommand[0] ) {
                    case "delete":
                        File toDelete = new File( libsFolder, splitCommand[1] );
                        if ( toDelete.exists() ) {
                            if ( !toDelete.delete() ) {
                                LOGGER.error( "Could not delete old version of required lib. Please delete {}", splitCommand[1] );
                                System.exit( -1 );
                            } else {
                                LOGGER.info( "Deleted old version of requried lib {}", splitCommand[1] );
                            }
                        }

                        break;

                    case "download":
                        String libURL = splitCommand[1];

                        // Head first to get informations about the file
                        URL url = new URL( libURL );
                        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                        urlConnection.setConnectTimeout( 1000 );
                        urlConnection.setReadTimeout( 1000 );
                        urlConnection.setRequestMethod( "HEAD" );

                        // Filter out non java archive content types
                        if ( !"application/java-archive".equals( urlConnection.getHeaderField( "Content-Type" ) ) ) {
                            LOGGER.debug( "Skipping the download of {} because its not a Java Archive", libURL );
                            continue;
                        }

                        // We need the contentLength to compare
                        int contentLength = Integer.parseInt( urlConnection.getHeaderField( "Content-Length" ) );

                        String[] tempSplit = url.getPath().split( "/" );
                        String fileName = tempSplit[tempSplit.length - 1];

                        // Check if we have a file with the same length
                        File libFile = new File( libsFolder, fileName );
                        if ( libFile.exists() && libFile.length() == contentLength ) {
                            LOGGER.debug( "Skipping the download of {} because there already is a correct sized copy", libURL );
                            continue;
                        }

                        // Download the file from the Server
                        Files.copy( url.openStream(), libFile.toPath(), StandardCopyOption.REPLACE_EXISTING );
                        LOGGER.info( "Downloading library: {}", fileName );
                        break;
                }
            }
        } catch ( IOException e ) {
            LOGGER.error( "Could not download needed library: ", e );
        }
    }

    /**
     * Appends a JAR into the System Classloader
     *
     * @param moduleFile which should be added to the classpath
     * @throws IOException
     */
    private static void addJARToClasspath( File moduleFile ) throws IOException {
        URL moduleURL = moduleFile.toURI().toURL();
        Class[] parameters = new Class[]{URL.class};

        ClassLoader sysloader = ClassLoader.getSystemClassLoader();
        Class sysclass = URLClassLoader.class;

        try {
            Method method = sysclass.getDeclaredMethod( "addURL", parameters );
            method.setAccessible( true );
            method.invoke( sysloader, new Object[]{moduleURL} );
        } catch ( NoSuchMethodException | InvocationTargetException | IllegalAccessException e ) {
            e.printStackTrace();
            throw new IOException( "Error, could not add URL to system classloader" );
        }
    }

    /**
     * Downloads a library file from the given url
     *
     * @param libsFolder The folder where the libraries will be copied into
     * @param dependency The dependency url to download from
     */
    private static void downloadLibraryFile( File libsFolder, String dependency ) {
        HttpURLConnection urlConnection = null;
        try {
            // Head first to get information about the file
            URL url = new URL( dependency );
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod( "HEAD" );

            // Filter out non java archive content types
            if ( !"application/java-archive".equals( urlConnection.getHeaderField( "Content-Type" ) ) ) {
                System.out.println( "Skipping the download of " + dependency + " because its not a Java Archive" );
                return;
            }

            // We need the contentLength to compare
            int contentLength = Integer.parseInt( urlConnection.getHeaderField( "Content-Length" ) );

            String[] tempSplit = url.getPath().split( "/" );
            String fileName = tempSplit[tempSplit.length - 1];

            // Check if we have a file with the same length
            File libFile = new File( libsFolder, fileName );
            if ( libFile.exists() && libFile.length() == contentLength ) {
                System.out.println( "Skipping the download of " + dependency + " because there already is a correct sized copy" );
                return;
            }

            // Download the file from the Server
            Files.copy( url.openStream(), libFile.toPath(), StandardCopyOption.REPLACE_EXISTING );
            System.out.println( "Downloading library: " + fileName );
        } catch ( IOException e ) {
            e.printStackTrace();
        }
    }
}