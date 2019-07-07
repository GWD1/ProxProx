/*
 * Copyright (c) 2016, GoMint, BlackyPaw and geNAZt
 *
 * This code is licensed under the BSD license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.gomint.proxprox.plugin;

import io.gomint.proxprox.api.plugin.PluginMeta;
import io.gomint.proxprox.api.plugin.PluginVersion;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.ClassFile;
import javassist.bytecode.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * @author geNAZt
 * @version 1.0
 */
public class PluginAutoDetector {
    private static final Logger logger = LoggerFactory.getLogger( PluginAutoDetector.class );

    /**
     * Check if the jar given is useable as plugin
     *
     * @param jarFile The jar file which should be checked
     * @return a loaded plugin meta or null when not usable as plugin
     */
    public PluginMeta checkPlugin( JarFile jarFile ) {
        Enumeration<JarEntry> jarEntries = jarFile.entries();

        if ( jarEntries == null ) {
            logger.warn( "Could not load Plugin. File " + jarFile + " is empty" );
            return null;
        }

        try {
            while ( jarEntries.hasMoreElements() ) {
                JarEntry jarEntry = jarEntries.nextElement();

                if ( jarEntry != null && jarEntry.getName().endsWith( ".class" ) ) {
                    ClassFile classFile = new ClassFile( new DataInputStream( jarFile.getInputStream( jarEntry ) ) );
                    if ( classFile.getSuperclass().equals( "io.gomint.proxprox.api.plugin.Plugin" ) ) {
                        PluginMeta pluginDescription = new PluginMeta();
                        pluginDescription.setName( classFile.getName().substring( classFile.getName().lastIndexOf( '.' ) + 1 ) );

                        AnnotationsAttribute visible = (AnnotationsAttribute) classFile.getAttribute( AnnotationsAttribute.visibleTag );

                        for ( Annotation annotation : visible.getAnnotations() ) {
                            switch ( annotation.getTypeName() ) {
                                case "io.gomint.proxprox.api.plugin.annotation.Description":
                                    pluginDescription.setDescription( ( (StringMemberValue) annotation.getMemberValue( "value" ) ).getValue() );
                                    break;

                                case "io.gomint.proxprox.api.plugin.annotation.Version":
                                    int major = ( ( IntegerMemberValue ) annotation.getMemberValue( "major" ) ).getValue();
                                    int minor = ( ( IntegerMemberValue ) annotation.getMemberValue( "minor" ) ).getValue();
                                    pluginDescription.setVersion( new PluginVersion( major, minor ) );
                                    break;

                                case "io.gomint.proxprox.api.plugin.annotation.Depends":
                                    MemberValue[] dependsValues = ( (ArrayMemberValue) annotation.getMemberValue( "value" ) ).getValue();
                                    HashSet<String> dependsStringValues = new HashSet<>();

                                    for ( MemberValue value : dependsValues ) {
                                        dependsStringValues.add( ( (StringMemberValue) value ).getValue() );
                                    }

                                    pluginDescription.setDepends( dependsStringValues );
                                    break;

                                case "io.gomint.proxprox.api.plugin.annotation.Name":
                                    pluginDescription.setName( ( (StringMemberValue) annotation.getMemberValue( "value" ) ).getValue() );
                                    break;

                                default:
                                    break;
                            }
                        }

                        pluginDescription.setMainClass( classFile.getName() );

                        return pluginDescription;
                    }
                }
            }

            return null;
        } catch ( IOException e ) {
            logger.warn( "Could not load Plugin. File " + jarFile + " is corrupted", e );
            return null;
        }
    }
}
