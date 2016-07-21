/*
 * Copyright (c) 2016, GoMint, BlackyPaw and geNAZt
 *
 * This code is licensed under the BSD license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.gomint.proxprox.api.data;

import io.gomint.proxprox.api.entity.Server;
import lombok.AllArgsConstructor;

/**
 * @author geNAZt
 * @version 1.0
 */
@AllArgsConstructor
public class ServerDataHolder implements Server {

    private String ip;
    private int port;

    @Override
    public String getIP() {
        return this.ip;
    }

    @Override
    public int getPort() {
        return this.port;
    }

}
