package de.dosmike.sponge.toomuchstock.utils;

import org.spongepowered.api.data.DataContainer;
import org.spongepowered.api.data.DataQuery;
import org.spongepowered.api.item.ItemType;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;

import java.util.function.Predicate;

/** can be config serialized */
public class ApplicabilityFilters implements Predicate<ItemStackSnapshot> {

    private static final DataQuery dqUnsafeDamage = DataQuery.of("UnsafeDamage");
    private static final DataQuery dqStackCount = DataQuery.of("Count");

    private String comparatorName;
    private Object template;
    private Predicate<ItemStackSnapshot> test;
    private ApplicabilityFilters(Object template, String comparatorName, Predicate<ItemStackSnapshot> predicate) {
        this.comparatorName = comparatorName;
        this.template = template;
        this.test = predicate;
    }

    @Override
    public boolean test(ItemStackSnapshot itemStackSnapshot) {
        return test.test(itemStackSnapshot);
    }

    @Override
    public String toString() {
        return "ApplicabilityFilters{" +
                "comparatorName='" + comparatorName + '\'' +
                ", template=" + template +
                ", test=" + test +
                '}';
    }

    public static ApplicabilityFilters generateItemTypeEquals(ItemType itemtype) {
        return new ApplicabilityFilters(itemtype, "ItemType", new Predicate<ItemStackSnapshot>() {
            final ItemType type = itemtype;
            @Override
            public boolean test(ItemStackSnapshot itemStack) {
                return itemStack.getType().equals(type);
            }
            @Override
            public String toString() {
                return "Predicate{" +
                        "test=ItemType, type=" + type +
                        '}';
            }
        });
    }

    public static ApplicabilityFilters generateItemTypeMetaEquals(ItemTypeEx itemtype) {
        return new ApplicabilityFilters(itemtype, "ItemTypeEx", new Predicate<ItemStackSnapshot>() {
            final ItemTypeEx type = itemtype;
            @Override
            public boolean test(ItemStackSnapshot itemStack) {
                return itemtype.equals(itemStack);
            }
            @Override
            public String toString() {
                return "Predicate{" +
                        "test=ItemTypeEx, type=" + type +
                        '}';
            }
        });
    }

    /** ignores quantity */
    public static ApplicabilityFilters generateContainerExactEquals(ItemStackSnapshot template) {
        return new ApplicabilityFilters(template, "ItemType", new Predicate<ItemStackSnapshot>() {
            final ItemType type = template.getType();
            final DataContainer container = template.toContainer().remove(dqStackCount);
            @Override
            public boolean test(ItemStackSnapshot itemStack) {
                DataContainer thiscont = template.toContainer().remove(dqStackCount);
                return itemStack.getType().equals(type) && container.equals(thiscont);
            }
            @Override
            public String toString() {
                return "Predicate{" +
                        "test=Container, type=" + type +
                        ", container=" + container +
                        '}';
            }
        });
    }

    public static final ApplicabilityFilters pass = new ApplicabilityFilters(null, "Pass", new Predicate<ItemStackSnapshot>() {
        @Override
        public boolean test(ItemStackSnapshot t) {
            return true;
        }
        @Override
        public String toString() {
            return "Predicate{test=Pass}";
        }
    });

}
