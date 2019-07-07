/*
 * Copyright (c) 2016, GoMint, BlackyPaw and geNAZt
 *
 * This code is licensed under the BSD license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.gomint.proxprox.api;

/**
 * @author geNAZt
 * @version 1.0
 */
public final class ChatColor {

    private static final char ESCAPE = '\u00a7';

    public static final String BLACK = ChatColor.ESCAPE + "0";
    public static final String DARK_BLUE = ChatColor.ESCAPE + "1";
    public static final String DARK_GREEN = ChatColor.ESCAPE + "2";
    public static final String DARK_AQUA = ChatColor.ESCAPE + "3";
    public static final String DARK_RED = ChatColor.ESCAPE + "4";
    public static final String DARK_PURPLE = ChatColor.ESCAPE + "5";
    public static final String GOLD = ChatColor.ESCAPE + "6";
    public static final String GRAY = ChatColor.ESCAPE + "7";
    public static final String DARK_GRAY = ChatColor.ESCAPE + "8";
    public static final String BLUE = ChatColor.ESCAPE + "9";
    public static final String GREEN = ChatColor.ESCAPE + "a";
    public static final String AQUA = ChatColor.ESCAPE + "b";
    public static final String RED = ChatColor.ESCAPE + "c";
    public static final String LIGHT_PURPLE = ChatColor.ESCAPE + "d";
    public static final String YELLOW = ChatColor.ESCAPE + "e";
    public static final String WHITE = ChatColor.ESCAPE + "f";

    public static final String OBFUSCATED = ChatColor.ESCAPE + "k";
    public static final String BOLD = ChatColor.ESCAPE + "l";
    public static final String STRIKETHROUGH = ChatColor.ESCAPE + "m";
    public static final String UNDERLINE = ChatColor.ESCAPE + "n";
    public static final String ITALIC = ChatColor.ESCAPE + "o";
    public static final String RESET = ChatColor.ESCAPE + "r";

    /**
     * Safe the string for MC:PE usage and remove colors and format codes
     *
     * @param message The message to clean
     * @return Cleaned up string
     */
    public static String clean( String message ) {
        return clean( message, true );
    }

    /**
     * Safe the string for MC:PE usage (and remove colors and format codes if you want)
     *
     * @param message The message to clean
     * @param removeFormat Should we remove formats and colors?
     * @return Cleaned up string
     */
    public static String clean( String message, boolean removeFormat ) {
        message = message.replaceAll( (char) 0x1b + "[0-9;\\[\\(]+[Bm]", "" );
        return removeFormat ? message.replaceAll( ESCAPE + "[0123456789abcdefklmnor]", "" ) : message;
    }

    /**
     * Replace the colors to ANSII colors for the console
     *
     * @param string colored string which should be converted
     * @return ANSII converted String
     */
    public static String toANSI( String string ) {
        string = string.replace( ChatColor.BOLD, "" );
        string = string.replace( ChatColor.OBFUSCATED, (char) 0x1b + "[8m" );
        string = string.replace( ChatColor.ITALIC, (char) 0x1b + "[3m" );
        string = string.replace( ChatColor.UNDERLINE, (char) 0x1b + "[4m" );
        string = string.replace( ChatColor.STRIKETHROUGH, (char) 0x1b + "[9m" );
        string = string.replace( ChatColor.RESET, (char) 0x1b + "[0m" );
        string = string.replace( ChatColor.BLACK, (char) 0x1b + "[0;30m" );
        string = string.replace( ChatColor.DARK_BLUE, (char) 0x1b + "[0;34m" );
        string = string.replace( ChatColor.DARK_GREEN, (char) 0x1b + "[0;32m" );
        string = string.replace( ChatColor.DARK_AQUA, (char) 0x1b + "[0;36m" );
        string = string.replace( ChatColor.DARK_RED, (char) 0x1b + "[0;31m" );
        string = string.replace( ChatColor.DARK_PURPLE, (char) 0x1b + "[0;35m" );
        string = string.replace( ChatColor.GOLD, (char) 0x1b + "[0;33m" );
        string = string.replace( ChatColor.GRAY, (char) 0x1b + "[0;37m" );
        string = string.replace( ChatColor.DARK_GRAY, (char) 0x1b + "[30;1m" );
        string = string.replace( ChatColor.BLUE, (char) 0x1b + "[34;1m" );
        string = string.replace( ChatColor.GREEN, (char) 0x1b + "[32;1m" );
        string = string.replace( ChatColor.AQUA, (char) 0x1b + "[36;1m" );
        string = string.replace( ChatColor.RED, (char) 0x1b + "[31;1m" );
        string = string.replace( ChatColor.LIGHT_PURPLE, (char) 0x1b + "[35;1m" );
        string = string.replace( ChatColor.YELLOW, (char) 0x1b + "[33;1m" );
        string = string.replace( ChatColor.WHITE, (char) 0x1b + "[37;1m" );
        return string;
    }

}