package de.dosmike.sponge.toomuchstock.utils;

import org.spongepowered.api.data.DataContainer;
import org.spongepowered.api.data.DataQuery;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.item.ItemType;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;

import java.util.function.Predicate;

public class ApplicabilityFilters {

    private static final DataQuery dqUnsafeDamage = DataQuery.of("UnsafeDamage");
    private static final DataQuery dqStackCount = DataQuery.of("Count");

    public static Predicate<ItemStackSnapshot> generateItemTypeEquals(ItemType itemtype) {
        return new Predicate<ItemStackSnapshot>() {
            final ItemType type = itemtype;
            @Override
            public boolean test(ItemStackSnapshot itemStack) {
                return itemStack.getType().equals(type);
            }
        };
    }

    public static Predicate<ItemStackSnapshot> generateItemTypeMetaEquals(ItemTypeEx itemtype) {
        return new Predicate<ItemStackSnapshot>() {
            final ItemTypeEx type = itemtype;
            @Override
            public boolean test(ItemStackSnapshot itemStack) {
                return itemtype.equals(itemStack);
            }
        };
    }

    /** ignores quantity */
    public static Predicate<ItemStackSnapshot> generateContainerExactEquals(ItemStackSnapshot template) {
        return new Predicate<ItemStackSnapshot>() {
            final ItemType type = template.getType();
            final DataContainer container = template.toContainer().remove(dqStackCount);
            @Override
            public boolean test(ItemStackSnapshot itemStack) {
                DataContainer thiscont = template.toContainer().remove(dqStackCount);
                return itemStack.getType().equals(type) && container.equals(thiscont);
            }
        };
    }

    public static final Predicate<ItemStackSnapshot> pass = (ItemStackSnapshot t)->true;

}
