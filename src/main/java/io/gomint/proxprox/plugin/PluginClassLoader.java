/*
 * Copyright (c) 2016, GoMint, BlackyPaw and geNAZt
 *
 * This code is licensed under the BSD license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.gomint.proxprox.plugin;

import io.gomint.proxprox.ProxProx;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * @author geNAZt
 * @version 1.0
 */
public class PluginClassLoader extends URLClassLoader {

    private static final Set<PluginClassLoader> ALL_LOADERS = new CopyOnWriteArraySet<>();
    private static ClassLoader applicationClassloader;

    static {
        ClassLoader.registerAsParallelCapable();

        applicationClassloader = ProxProx.class.getClassLoader();
    }

    /**
     * Construct new Classloader. This blocks all Jars given
     *
     * @param urls A array of JAR files to handle in this Classloader
     */
    public PluginClassLoader( URL[] urls ) {
        super( urls );
        ALL_LOADERS.add( this );
    }

    @Override
    protected Class<?> loadClass( String name, boolean resolve ) throws ClassNotFoundException {
        return loadClass0( name, resolve, true );
    }

    private Class<?> loadClass0( String name, boolean resolve, boolean checkOther ) throws ClassNotFoundException {
        try {
            return super.loadClass( name, resolve );
        } catch ( ClassNotFoundException ex ) {
            // Ignored
        }

        if ( checkOther ) {
            for ( PluginClassLoader loader : ALL_LOADERS ) {
                if ( loader != this ) {
                    try {
                        return loader.loadClass0( name, resolve, false );
                    } catch ( ClassNotFoundException ex ) {
                        // Ignored
                    }
                }
            }

            try {
                return applicationClassloader.loadClass( name );
            } catch ( ClassNotFoundException ex ) {
                // Ignored
            }
        }

        throw new ClassNotFoundException( name );
    }

    /**
     * Remove this classloader as we want to release the JAR file
     */
    public void remove() {
        ALL_LOADERS.remove( this );

        try {
            super.close();
        } catch ( IOException e ) {
            e.printStackTrace();
        }
    }

}
