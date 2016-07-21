/*
 * Copyright (c) 2016, GoMint, BlackyPaw and geNAZt
 *
 * This code is licensed under the BSD license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.gomint.proxprox.api.command;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * @author geNAZt
 * @version 1.0
 */
@AllArgsConstructor
public class ConsoleCommandSender implements CommandSender {

    private final Logger logger = LoggerFactory.getLogger( ConsoleCommandSender.class );

    @Override
    public void sendMessage( String... messages ) {
        for ( String message : messages ) {
            logger.info( message );
        }
    }

    @Override
    public String getName() {
        return "Console";
    }

    @Override
    public boolean hasPermission( String permission ) {
        return true;
    }

    @Override
    public String getColor() {
        return "§4";
    }

    @Override
    public String getPrefix() {
        return "§4";
    }

    @Override
    public Locale getLocale() {
        return Locale.US;
    }

    @Override
    public long getLastCommandTime() {
        return 0;
    }

    @Override
    public void increaseCommandIssues( String command ) {

    }

    @Override
    public void resetCommandIssues() {

    }

}
