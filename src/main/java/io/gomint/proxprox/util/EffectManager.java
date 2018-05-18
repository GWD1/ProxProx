package io.gomint.proxprox.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * @author geNAZt
 * @version 1.0
 */
public class EffectManager {

    private Map<Integer, Long> activeEffects = new HashMap<>();

    public void add( Integer effect, long endTime ) {
        this.activeEffects.put( effect, endTime );
    }

    public void remove( int effectId ) {
        this.activeEffects.remove( effectId );
    }

    public List<Integer> getEffects() {
        List<Integer> validEffects = new ArrayList<>();
        this.activeEffects.forEach( new BiConsumer<Integer, Long>() {
            @Override
            public void accept( Integer integer, Long aLong ) {
                if ( System.currentTimeMillis() < aLong ) {
                    validEffects.add( integer );
                }
            }
        } );
        return validEffects;
    }

}
