package io.gomint.proxprox.api.config.converter;

import io.gomint.proxprox.api.config.BaseConfigMapper;
import io.gomint.proxprox.api.config.Comment;
import io.gomint.proxprox.api.config.Comments;
import io.gomint.proxprox.api.config.ConfigSection;
import io.gomint.proxprox.api.config.InternalConverter;
import io.gomint.proxprox.api.config.YamlConfig;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.LinkedHashMap;
import java.util.Map;

public class ConfigConverter implements Converter {

    private InternalConverter internalConverter;

    public ConfigConverter( InternalConverter internalConverter ) {
        this.internalConverter = internalConverter;
    }

    @Override
    public Object toConfig( Class<?> type, Object obj, ParameterizedType parameterizedType ) throws Exception {
        if ( obj instanceof Map ) {
            return obj;
        }

        // We need to extract comments
        Map<String, String> comments = new LinkedHashMap<>();
        for ( Field field : type.getDeclaredFields() ) {
            String comment = "";

            if ( field.isAnnotationPresent( Comment.class ) ) {
                comment = field.getAnnotation( Comment.class ).value();
            } else if ( field.isAnnotationPresent( Comments.class ) ) {
                for ( Comment comment1 : field.getAnnotation( Comments.class ).value() ) {
                    comment += comment1.value() + "\n";
                }
            }

            if ( !comment.isEmpty() ) {
                comments.put( field.getName(), comment );
            }
        }

        if ( this.internalConverter.getConfig() instanceof BaseConfigMapper ) {
            BaseConfigMapper baseConfigMapper = (BaseConfigMapper) this.internalConverter.getConfig();
            baseConfigMapper.mergeComments( comments );
        }

        return ( (YamlConfig) obj ).saveToMap( obj.getClass() );
    }

    @Override
    public Object fromConfig( Class type, Object section, ParameterizedType genericType ) throws Exception {
        YamlConfig obj = (YamlConfig) newInstance( type );

        // Inject converter stack into sub config
        for ( Class aClass : this.internalConverter.getCustomConverters() ) {
            obj.addConverter( aClass );
        }

        obj.loadFromMap( ( section instanceof Map ) ? (Map) section : ( (ConfigSection) section ).getRawMap(), type );
        return obj;
    }

    // recursively handles enclosed classes
    public Object newInstance( Class type ) throws Exception {
        Class enclosingClass = type.getEnclosingClass();
        if ( enclosingClass != null ) {
            Object instanceOfEnclosingClass = newInstance( enclosingClass );
            return type.getConstructor( enclosingClass ).newInstance( instanceOfEnclosingClass );
        } else {
            return type.newInstance();
        }
    }

    @Override
    public boolean supports( Class<?> type ) {
        return YamlConfig.class.isAssignableFrom( type );
    }
}