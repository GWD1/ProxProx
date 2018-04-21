/*
 * Copyright (c) 2016, GoMint, BlackyPaw and geNAZt
 *
 * This code is licensed under the BSD license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.gomint.proxprox.config;

import com.blackypaw.simpleconfig.SimpleConfig;
import com.blackypaw.simpleconfig.annotation.Comment;
import lombok.Data;

/**
 * @author geNAZt
 * @version 1.0
 */
@Data
public class ProxyConfig extends SimpleConfig {

    private String ip = "0.0.0.0";
    private int port = 19132;

    private ServerConfig defaultServer = new ServerConfig( "127.0.0.1", 19134 );

    // If onlineMode = true the Proxy kicks players which don't have a xbox payload in their login or the login
    // in general is not valid (certification issues)
    private boolean onlineMode = false;

    @Comment("Disable encryption? This does reduce the amount of CPU needed, also it seems to cause more stability" )
    private boolean disableEncryption = true;

    @Comment("Gomint servers can use TCP listeners instead of Raknet to safe additional network delay and encryption overheads")
    private boolean useTCP = true;

}
