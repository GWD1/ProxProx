package io.gomint.proxprox.api.event;

import io.gomint.proxprox.api.entity.Player;
import io.gomint.proxprox.api.plugin.event.Event;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * @author geNAZt
 * @version 1.0
 */
@RequiredArgsConstructor
@Getter
public class PlayerQuitEvent extends Event {

    private final Player player;

}
