/*
 * Copyright (c) 2016, GoMint, BlackyPaw and geNAZt
 *
 * This code is licensed under the BSD license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.gomint.proxprox.api.entity;

import io.gomint.proxprox.api.command.CommandSender;

import java.net.InetSocketAddress;
import java.util.Locale;
import java.util.UUID;

/**
 * @author geNAZt
 * @version 1.0
 */
public interface Player extends CommandSender {

    UUID getUUID();

    String getName();

    Server getServer();

    /**
     * Connect to a new DownStream server
     *
     * @param ip   The ip of the server
     * @param port The port of the server
     */
    void connect(String ip, int port);

    boolean isValid();

    String getXboxId();

    InetSocketAddress getAddress();

    <T> T getMetaData(String key);
    void setMetaData(String key, Object data);

    long getPing();

    Locale getLocale();

    void kick(String reason);

}
