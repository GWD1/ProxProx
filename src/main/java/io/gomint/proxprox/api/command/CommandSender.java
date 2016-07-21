/*
 * Copyright (c) 2016, GoMint, BlackyPaw and geNAZt
 *
 * This code is licensed under the BSD license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.gomint.proxprox.api.command;

import java.util.Locale;

/**
 * @author geNAZt
 * @version 1.0
 */
public interface CommandSender {
    void sendMessage( String... messages );
    String getName();
    boolean hasPermission( String permission );
    String getColor();
    String getPrefix();
    Locale getLocale();
    long getLastCommandTime();
    void increaseCommandIssues( String command );
    void resetCommandIssues();
}
