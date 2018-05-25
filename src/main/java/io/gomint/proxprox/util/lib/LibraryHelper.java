package io.gomint.proxprox.util.lib;

import io.gomint.proxprox.Bootstrap;
import io.gomint.proxprox.util.lib.handler.DeleteAllLibraryActionHandler;
import io.gomint.proxprox.util.lib.handler.DeleteLibraryActionHandler;
import io.gomint.proxprox.util.lib.handler.DownloadLibraryActionHandler;
import io.gomint.proxprox.util.lib.handler.LibraryActionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

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
 * @version 1.0.1
 */
public final class LibraryHelper {

    private static class ActionRegistry {
        /* Registered handlers */
        private static final Map<String, Class<? extends LibraryActionHandler>> ACTION_HANDLERS = new HashMap<>();
        private static final Logger LOGGER = LoggerFactory.getLogger(ActionRegistry.class);

        // ====================================== DEFAULT HANDLERS ====================================== //
        static {
            assignHandler("download", DownloadLibraryActionHandler.class);
            assignHandler("delete", DeleteLibraryActionHandler.class);
            assignHandler("deleteAll", DeleteAllLibraryActionHandler.class);
        }

        // TODO Block default handler overwrite
        /* For now, only protected access granted until a implementation were made not overriding default handlers */
        static void assignHandler(String action, Class<? extends LibraryActionHandler> handlerClass) {
            if (action == null) { LOGGER.warn("Failed assigning handler: 'action' is referring to null"); return;}
            if (handlerClass == null) { LOGGER.warn("Failed assigning handler: 'handlerClass' is referring to null"); return;}

            /* Avoid having pointless exceptions when instantiating a handler */
            if (handlerClass.isInterface() || Modifier.isAbstract(handlerClass.getModifiers())) {
                LOGGER.warn("Failed assigning handler: Class cannot be an interface nor can it be abstract");
                return;
            }

            ACTION_HANDLERS.put(action, handlerClass);
            LOGGER.debug("Assignment was made: '" + action + "' <- " + handlerClass.getName());
        }

        static LibraryActionHandler getHandler(String action) {
            if (action == null) { LOGGER.debug("Failed gathering handler: 'action' is referring to null"); return null;}
            if (ACTION_HANDLERS.isEmpty()) { return null; }

            Class<? extends LibraryActionHandler> handlerClass = ACTION_HANDLERS.get(action);
            LibraryActionHandler libraryActionHandler = null;

            // TODO Store single instance of action handler, stop instantiating every time a new action handler
            try {
                Constructor constructor = handlerClass.getDeclaredConstructor();
                constructor.setAccessible(true);

                libraryActionHandler = (LibraryActionHandler) constructor.newInstance();
            } catch (Exception e) {
                LOGGER.error("Encountered errors while instantiating action handler:", e);
            }

            return libraryActionHandler;
        }

    }

    public static final File LIBRARY_DIRECTORY = new File("libs/");
    public static final Logger LOGGER = LoggerFactory.getLogger(LibraryHelper.class);

    public void checkDepFile() {
        // Check if we are able to skip this
        if (System.getProperty("skip.libCheck", "false").equals("true")) { return; }

        try(BufferedReader reader = getBufferedReaderOfDepFile()) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Check for comment, if commented skip action
                if (line.isEmpty() || line.equals(System.getProperty("line.separator")) || line.startsWith("#")) { continue; }

                String[] actionCompounds = line.split("~");
                String[] handlerArgs = Arrays.copyOfRange(actionCompounds, 1, actionCompounds.length);

                dispatchAction(actionCompounds[0], handlerArgs);
            }
        } catch (IOException e) {
            LOGGER.error("Failed checking dependency file: ", e);
        }
    }

    // ====================================== DOWNLOAD ====================================== //

    /** @deprecated Planed for removal. Not being used as {@link Bootstrap} is using the {@link #checkDepFile()} method */
    @Deprecated
    public void download(URL libraryUrl) {
        dispatchAction("download", libraryUrl.toString(), LIBRARY_DIRECTORY.toString());
    }

    /** @deprecated Planed for removal. Not being used as {@link Bootstrap} is using the {@link #checkDepFile()} method */
    @Deprecated
    public void download(String libraryUrl) {
        dispatchAction("download", libraryUrl, LIBRARY_DIRECTORY.toString());
    }

    // ====================================== DELETE ====================================== //

    /** @deprecated Planed for removal. Not being used as {@link Bootstrap} is using the {@link #checkDepFile()} method */
    @Deprecated
    public void delete(String libraryName) {
        dispatchAction("delete", libraryName, LIBRARY_DIRECTORY.toString());
    }

    /** @deprecated Planed for removal. Not being used as {@link Bootstrap} is using the {@link #checkDepFile()} method */
    @Deprecated
    public void deleteAll() {
        dispatchAction("deleteAll", LIBRARY_DIRECTORY.toString());
    }

    public void dispatchAction(String action, String... args) {
        LibraryActionHandler handler = ActionRegistry.getHandler(action);
        if (handler == null) { LOGGER.warn("Could not dispatch action: No handler available for '" + action + "'"); return; }

        handler.handleAction(action, LIBRARY_DIRECTORY, args);
    }

    /* Appends a Jar file to the classpath from ClassLoader#getSystemClassLoader() */
    public void addJarToClasspath(File jarFile) throws IOException {
        if (jarFile == null) { LOGGER.debug("Failed appending jar to classpath: 'jarFile' is referring to null"); return;}

        try {
            Method addUrlMethod = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            addUrlMethod.setAccessible(true);
            addUrlMethod.invoke(ClassLoader.getSystemClassLoader(), jarFile.toURI().toURL());
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            LOGGER.error("Failed appending jar to classpath: ", e);
        }
    }

    private BufferedReader getBufferedReaderOfDepFile() {
        return new BufferedReader(new InputStreamReader(Bootstrap.class.getResourceAsStream("/libs.dep")));
    }

}
