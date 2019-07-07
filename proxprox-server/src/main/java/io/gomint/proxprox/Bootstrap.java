/*
 * Copyright (c) 2016, GoMint, BlackyPaw and geNAZt
 *
 * This code is licensed under the BSD license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.gomint.proxprox;

import io.gomint.proxprox.util.RuntimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;

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

    /**
     * Main entry point. May be used for custom dependency injection, dynamic
     * library class loaders and other experiments which need to be done before
     * the actual main entry point is executed.
     *
     * @param args The command-line arguments to be passed to the entryClass
     */
    public static void main( String[] args ) {
        // Check if we need to create the libs Folder
        File libraryDirectory = new File( "./libs" );
        if ( !libraryDirectory.exists() && !libraryDirectory.mkdirs() ) {
            System.out.println( "Failed creating library directory" );
            System.exit( -1 );
        }

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
                    RuntimeUtil.addJarToClasspath( file );
                } catch ( IOException e ) {
                    e.printStackTrace();
                }
            }
        }

        // Load the class entry point
        try {
            Class<?> coreClass = ClassLoader.getSystemClassLoader().loadClass( "io.gomint.proxprox.ProxProxProxy" );
            Constructor constructor = coreClass.getDeclaredConstructor( String[].class );
            constructor.newInstance( new Object[]{ args } );
        } catch ( Exception e ) {
            e.printStackTrace();
        }
    }

}