package io.gomint.proxprox.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

public class RuntimeUtil {

    private static Logger LOGGER = LoggerFactory.getLogger(RuntimeUtil.class);

    /**
     * Appends a Jar file to the classpath from ClassLoader#getSystemClassLoader()
     *
     * @param jarFile The file which should be appended to the classpath
     */
    public static void addJarToClasspath(File jarFile) throws IOException {
        if (jarFile == null) { LOGGER.error("Failed appending jar to classpath: 'jarFile' is referring to null"); return;}

        try {
            Method addUrlMethod = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            addUrlMethod.setAccessible(true);
            addUrlMethod.invoke(ClassLoader.getSystemClassLoader(), jarFile.toURI().toURL());
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            LOGGER.error("Failed appending jar to classpath: ", e);
        }
    }


}
