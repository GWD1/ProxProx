/*
 * Copyright (c) 2016, GoMint, BlackyPaw and geNAZt
 *
 * This code is licensed under the BSD license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.gomint.proxprox.api.plugin;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * @author BlackyPaw
 * @version 1.0
 */
@Getter
@RequiredArgsConstructor
public class PluginVersion implements Comparable<PluginVersion> {
    private final int major;
    private final int minor;

    @Override
    public int compareTo( @NonNull PluginVersion other ) {
        int diff = this.major - other.major;
        if ( diff != 0 ) {
            return diff;
        } else {
            return this.minor - other.minor;
        }
    }

    @Override
    public boolean equals( Object o ) {
        if ( o == this ) {
            return true;
        }

        if ( !( o instanceof PluginVersion ) ) {
            return false;
        }

        PluginVersion v = (PluginVersion) o;
        return (this.major == v.major && this.minor == v.minor);
    }

    @Override
    public int hashCode() {
        int hash = 17;
        hash = hash * 31 + this.major;
        hash = hash * 31 + this.minor;
        return hash;
    }

    @Override
    public String toString() {
        return this.major + "." + this.minor;
    }
}
