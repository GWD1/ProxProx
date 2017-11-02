package io.gomint.proxprox.network.protocol.type;

import lombok.Getter;

/**
 * @author geNAZt
 * @version 1.0
 */
public enum ResourceResponseStatus {

    REFUSED(1),
    SEND_PACKS(2),
    HAVE_ALL_PACKS(3),
    COMPLETED(4);

    @Getter
    private final byte id;
    ResourceResponseStatus( int id ) {
        this.id = (byte) id;
    }

    public static ResourceResponseStatus valueOf( int statusId ) {
        switch ( statusId ) {
            case 1:
                return REFUSED;
            case 2:
                return SEND_PACKS;
            case 3:
                return HAVE_ALL_PACKS;
            case 4:
                return COMPLETED;
        }

        return null;
    }

}
