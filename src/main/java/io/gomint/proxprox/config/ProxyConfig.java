/*
 * Copyright (c) 2016, GoMint, BlackyPaw and geNAZt
 *
 * This code is licensed under the BSD license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.gomint.proxprox.config;

import com.blackypaw.simpleconfig.SimpleConfig;

/**
 * @author geNAZt
 * @version 1.0
 */
public class ProxyConfig extends SimpleConfig {

    private String ip = "0.0.0.0";
    private int port = 19132;

    private ServerConfig defaultServer = new ServerConfig( "127.0.0.1", 19134 );

    // If onlineMode = true the Proxy kicks players which don't have a xbox payload in their login or the login
    // in general is not valid (certification issues)
    private boolean onlineMode = false;

    /**
     * Get the IP which should be used to bind to for incoming Upstream (User) Connections
     *
     * @return IP to bind to
     */
    public String getIp() {
        return ip;
    }

    /**
     * Override method for CLI Argument --ip
     *
     * @param ip The IP which should be used instead of the one in the config
     */
    public void setIp( String ip ) {
        this.ip = ip;
    }

    /**
     * Get the port (default 19132) which should be used to bind for accepting user connections
     *
     * @return Port to bind to
     */
    public int getPort() {
        return port;
    }

    /**
     * Override method for CLI Argument --port
     *
     * @param port The port which should be used instead of the one given in the config
     */
    public void setPort( int port ) {
        this.port = port;
    }

    /**
     * Get the config for the default server
     *
     * @return IP and Port to connect to the default server
     */
    public ServerConfig getDefaultServer() {
        return this.defaultServer;
    }

    /**
     * Are we in online mode or not?
     */
    public boolean isOnlineMode() {
        return this.onlineMode;
    }

}
