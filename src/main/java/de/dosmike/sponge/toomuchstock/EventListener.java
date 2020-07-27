package de.dosmike.sponge.toomuchstock;

import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.network.ClientConnectionEvent;

public class EventListener {

    @Listener
    public void onPlayerDisconnect(ClientConnectionEvent.Disconnect event) {
        TooMuchStock.getPriceCalculator().unloadPlayerState(event.getTargetEntity().getUniqueId());
    }

    @Listener
    public void onPlayerConnect(ClientConnectionEvent.Join event) {
        TooMuchStock.getPriceCalculator().loadPlayerState(event.getTargetEntity().getUniqueId());
    }

}
