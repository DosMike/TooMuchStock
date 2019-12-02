package de.dosmike.sponge.toomuchstock.maths;

import org.spongepowered.api.Sponge;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.event.impl.AbstractEvent;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;

public class PriceUpdateEvent extends AbstractEvent {

    private ItemStackSnapshot snapshot;
    private Cause cause;
    public PriceUpdateEvent(ItemStackSnapshot item) {
        snapshot = item;
        cause = Sponge.getCauseStackManager().getCurrentCause();
    }

    /**
     * Get the item that was purchased or sold as trigger to this event.<br>
     * This might be useful to filter the amount of actions you have to take following the price change.<br>
     * This value will be null if the price change was caused due to periodic decay!
     * @return the item triggering the update or null if this event is part of the discrepancy decay
     */
    public ItemStackSnapshot getSnapshot() {
        return snapshot;
    }

    @Override
    public Cause getCause() {
        return cause;
    }
}
