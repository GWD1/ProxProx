/*
 * Copyright (c) 2016, GoMint, BlackyPaw and geNAZt
 *
 * This code is licensed under the BSD license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.gomint.proxprox.api.command;

import lombok.Getter;

/**
 * @author geNAZt
 * @version 1.0
 */
public abstract class Command {

    @Getter private String name;
    @Getter private String description;
    @Getter private String[] aliases = new String[0];

    /**
     * Construct a new Command
     *
     * @param name          The name of the command
     * @param description   A description of the command
     * @param aliases       Possible aliases of the command
     */
    public Command( String name, String description, String... aliases ) {
        this.name = name;
        this.description = description;
        this.aliases = aliases;
    }

    /**
     * Invoked when the command should be executed
     *
     * @param sender The command sender which executed the command
     * @param args   Possible arguments for the command
     */
    public abstract void execute( CommandSender sender, String[] args );

}
