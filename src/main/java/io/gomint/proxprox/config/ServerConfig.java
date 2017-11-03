/*
 * Copyright (c) 2016, GoMint, BlackyPaw and geNAZt
 *
 * This code is licensed under the BSD license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.gomint.proxprox.config;

import com.blackypaw.simpleconfig.SimpleConfig;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * @author geNAZt
 * @version 1.0
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ServerConfig extends SimpleConfig {

    private String ip;
    private int port;

}
