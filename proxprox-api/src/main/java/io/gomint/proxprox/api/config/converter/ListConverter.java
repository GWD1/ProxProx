package io.gomint.proxprox.api.config.converter;

import io.gomint.proxprox.api.config.BaseConfigMapper;
import io.gomint.proxprox.api.config.InternalConverter;

import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;

public class ListConverter implements Converter {

    private InternalConverter internalConverter;

    public ListConverter( InternalConverter internalConverter ) {
        this.internalConverter = internalConverter;
    }

    @Override
    public Object toConfig( Class<?> type, Object obj, ParameterizedType genericType ) throws Exception {
        java.util.List values = (java.util.List) obj;
        java.util.List newList = new ArrayList();

        if ( this.internalConverter.getConfig() instanceof BaseConfigMapper ) {
            BaseConfigMapper baseConfigMapper = (BaseConfigMapper) this.internalConverter.getConfig();
            baseConfigMapper.addCommentPrefix( "-" );
        }

        for (Object val : values) {
            Converter converter = this.internalConverter.getConverter( val.getClass() );

            if ( converter != null ) {
                newList.add( converter.toConfig( val.getClass(), val, null ) );
            } else {
                newList.add( val );
            }
        }

        if ( this.internalConverter.getConfig() instanceof BaseConfigMapper ) {
            BaseConfigMapper baseConfigMapper = (BaseConfigMapper) this.internalConverter.getConfig();
            baseConfigMapper.removeCommentPrefix( "-" );
        }

        return newList;
    }

    @Override
    public Object fromConfig( Class type, Object section, ParameterizedType genericType ) throws Exception {
        java.util.List newList = new ArrayList();
        try {
            newList = ((java.util.List) type.newInstance());
        } catch ( Exception e ) {
        }

        java.util.List values = (java.util.List) section;

        if ( genericType != null && genericType.getActualTypeArguments()[0] instanceof Class ) {
            Converter converter = this.internalConverter.getConverter( (Class) genericType.getActualTypeArguments()[0] );

            if ( converter != null ) {
                for (int i = 0; i < values.size(); i++) {
                    newList.add( converter.fromConfig( (Class) genericType.getActualTypeArguments()[0], values.get( i ), null ) );
                }
            } else {
                newList = values;
            }
        } else {
            newList = values;
        }

        return newList;
    }

    @Override
    public boolean supports( Class<?> type ) {
        return java.util.List.class.isAssignableFrom( type );
    }
}