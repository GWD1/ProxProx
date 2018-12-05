package io.gomint.proxprox.config;

import io.gomint.proxprox.api.config.Comment;
import io.gomint.proxprox.api.config.YamlConfig;
import lombok.Data;

@Data
public class ProxyConfig extends YamlConfig {

    private String ip = "0.0.0.0";
    private int port = 19132;

    private ServerConfig defaultServer = new ServerConfig();

    @Comment("Motd of this server")
    private String motd = "§aProxProx §7Development Build";

    @Comment( "The maximum number of players to play on this server" )
    private int maxPlayers = 100;

    // If onlineMode = true the Proxy kicks players which don't have a xbox payload in their login or the login
    // in general is not valid (certification issues)
    private boolean onlineMode = true;

    @Comment("Disable encryption? This does reduce the amount of CPU needed, also it seems to cause more stability" )
    private boolean disableEncryption = false;

    @Comment("Gomint servers can use TCP listeners instead of Raknet to safe additional network delay and encryption overheads")
    private boolean useTCP = false;

}
