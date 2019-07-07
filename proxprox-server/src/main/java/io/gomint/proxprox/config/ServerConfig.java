package io.gomint.proxprox.config;

import io.gomint.proxprox.api.config.YamlConfig;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ServerConfig extends YamlConfig {

    private String ip = "127.0.0.1";
    private int port = 19133;

}
