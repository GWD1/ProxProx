package io.gomint.proxprox.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

public class RuntimeUtil {

    private static Logger LOGGER = LoggerFactory.getLogger( RuntimeUtil.class );

    /**
     * Appends a Jar file to the classpath from ClassLoader#getSystemClassLoader()
     *
     * @param jarFile The file which should be appended to the classpath
     */
    public static void addJarToClasspath( File jarFile ) throws IOException {
        if ( jarFile == null ) {
            LOGGER.error( "Failed appending jar to classpath: 'jarFile' is referring to null" );
            return;
        }

        URL moduleURL = jarFile.toURI().toURL();

        // Check if classloader has been changed (it should be a URLClassLoader)
        if ( !( ClassLoader.getSystemClassLoader() instanceof URLClassLoader ) ) {
            // This is invalid for Java 9/10, they use a UCP inside a wrapper loader
            try {
                Field ucpField = ClassLoader.getSystemClassLoader().getClass().getDeclaredField( "ucp" );
                ucpField.setAccessible( true );

                Object ucp = ucpField.get( ClassLoader.getSystemClassLoader() );
                Method addURLucp = ucp.getClass().getDeclaredMethod( "addURL", URL.class );
                addURLucp.invoke( ucp, moduleURL );
            } catch ( NoSuchFieldException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e ) {
                e.printStackTrace();
            }
        } else {
            Class[] parameters = new Class[]{ URL.class };

            ClassLoader sysloader = ClassLoader.getSystemClassLoader();
            Class sysclass = URLClassLoader.class;

            try {
                Method method = sysclass.getDeclaredMethod( "addURL", parameters );
                method.setAccessible( true );
                method.invoke( sysloader, new Object[]{ moduleURL } );
            } catch ( NoSuchMethodException | InvocationTargetException | IllegalAccessException e ) {
                e.printStackTrace();
                throw new IOException( "Error, could not add URL to system classloader" );
            }
        }
    }


}
