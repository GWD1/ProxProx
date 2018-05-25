/*
 * Copyright (c) 2016, GoMint, BlackyPaw and geNAZt
 *
 * This code is licensed under the BSD license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.gomint.proxprox;

import io.gomint.proxprox.util.lib.LibraryHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.URLClassLoader;

/**
 * This Bootstrap downloads all Libraries given inside of the "libs.dep" File in the Root
 * of the Application Workdir and then instanciates the Class which is given as Application
 * entry point.
 *
 * @author geNAZt
 * @author Shad0wCore
 * @version 1.0
 */
public final class Bootstrap {

    private static final Logger LOGGER = LoggerFactory.getLogger( Bootstrap.class );
    private static final LibraryHelper LIBRARY_HELPER = new LibraryHelper();;

    /**
     * Main entry point. May be used for custom dependency injection, dynamic
     * library class loaders and other experiments which need to be done before
     * the actual main entry point is executed.
     *
     * @param args The command-line arguments to be passed to the entryClass
     */
    public static void main( String[] args ) {
        new Bootstrap();

        // Check if classloader has been changed (it should be a URLClassLoader)
        if ( !( ClassLoader.getSystemClassLoader() instanceof URLClassLoader ) ) {
            System.out.println( "System Classloader is no URLClassloader" );
            System.exit( -1 );
        }

        // Check if we need to create the libs Folder
        File libraryDirectory = LibraryHelper.LIBRARY_DIRECTORY;
        if ( !libraryDirectory.exists() && !libraryDirectory.mkdirs() ) {
            System.out.println( "Failed creating library directory" );
            System.exit( -1 );
        }

        // Check the libs (versions and artifacts)
        LIBRARY_HELPER.checkDepFile();

        File[] files = libraryDirectory.listFiles();
        if ( files == null ) {
            System.out.println( "Library directory is corrupted" );
            System.exit( -1 );
        }

        // Scan the library directory for .jar Files
        for ( File file : files ) {
            if ( file.getAbsolutePath().endsWith( ".jar" ) ) {
                try {
                    LOGGER.info( "Loading library: " + file.getAbsolutePath() );
                    LIBRARY_HELPER.addJarToClasspath( file );
                } catch ( IOException e ) {
                    e.printStackTrace();
                }
            }
        }

        // Load the class entry point
        try {
            Class<?> coreClass = ClassLoader.getSystemClassLoader().loadClass( "io.gomint.proxprox.ProxProx" );
            Constructor constructor = coreClass.getDeclaredConstructor( String[].class );
            constructor.newInstance( new Object[]{args} );
        } catch ( Exception e ) {
            e.printStackTrace();
        }
    }

}