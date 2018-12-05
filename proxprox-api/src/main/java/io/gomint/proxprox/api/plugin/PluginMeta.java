/*
 * Copyright (c) 2016, GoMint, BlackyPaw and geNAZt
 *
 * This code is licensed under the BSD license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.gomint.proxprox.api.plugin;

import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.io.File;
import java.util.Set;

/**
 * @author BlackyPaw
 * @version 1.0
 */
@Data
public class PluginMeta {
    private String name;
    private String description;

    private PluginVersion version;
    private Set<String> depends;

    private String mainClass;
    private File pluginFile;
}
