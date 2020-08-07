package de.dosmike.sponge.toomuchstock.utils;

import com.google.common.base.Objects;
import de.dosmike.sponge.toomuchstock.ConfigKeys;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import org.spongepowered.api.data.DataContainer;
import org.spongepowered.api.data.DataQuery;
import org.spongepowered.api.item.ItemType;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;
import org.spongepowered.api.util.TypeTokens;

import java.util.Optional;
import java.util.function.Predicate;

/** can be config serialized */
public abstract class ApplicabilityFilters<T> implements Predicate<ItemStackSnapshot> {

    private static final DataQuery dqUnsafeDamage = DataQuery.of("UnsafeDamage");
    private static final DataQuery dqStackCount = DataQuery.of("Count");

    public static final String DEFAULT_FILTER_ITEMTYPE = "ItemType";
    public static final String DEFAULT_FILTER_ITEMTYPEMETA = "ItemTypeEx";
    public static final String DEFAULT_FILTER_ITEMNBT = "ItemNBT";

    final private String comparatorName;
    final protected T template;
    private ApplicabilityFilters(T template, String comparatorName) {
        this.comparatorName = comparatorName;
        this.template = template;
    }

    @Override
    public String toString() {
        return "ApplicabilityFilters{" +
                comparatorName + ' ' + template.toString() +
                '}';
    }

    public Optional<ItemStackSnapshot> generateTemplate() {
        if (template instanceof ItemStackSnapshot) {
            return Optional.of((ItemStackSnapshot) template);
        } else if (template instanceof ItemTypeEx) {
            return Optional.ofNullable(((ItemTypeEx) template).getTemplate());
        } else if (template instanceof ItemType) {
            return Optional.of(((ItemType) template).getTemplate());
        } else {
            return Optional.empty();
        }
    }

    public static ApplicabilityFilters<ItemType> generateItemTypeEquals(ItemType itemtype) {
        return new ApplicabilityFilters<ItemType>(itemtype, DEFAULT_FILTER_ITEMTYPE) {
            @Override
            public boolean test(ItemStackSnapshot itemStack) {
                return itemStack.getType().equals(template);
            }
        };
    }

    public static ApplicabilityFilters<ItemTypeEx> generateItemTypeMetaEquals(ItemTypeEx itemtype) {
        return new ApplicabilityFilters<ItemTypeEx>(itemtype, DEFAULT_FILTER_ITEMTYPEMETA) {
            @Override
            public boolean test(ItemStackSnapshot itemStack) {
                return template.equals(itemStack);
            }
        };
    }

    /** ignores quantity */
    public static ApplicabilityFilters<ItemStackSnapshot> generateContainerExactEquals(ItemStackSnapshot template) {
        return new ApplicabilityFilters<ItemStackSnapshot>(template, DEFAULT_FILTER_ITEMNBT) {
            final ItemType type = template.getType();
            final DataContainer container = template.toContainer().remove(dqStackCount);
            @Override
            public boolean test(ItemStackSnapshot itemStack) {
                DataContainer thiscont = template.toContainer().remove(dqStackCount);
                return itemStack.getType().equals(type) && container.equals(thiscont);
            }
        };
    }

    public static final ApplicabilityFilters<?> pass = new ApplicabilityFilters(null, "Pass") {
        @Override
        public boolean test(Object t) {
            return true;
        }
    };

    public void toConfiguration(ConfigurationNode parent) throws ObjectMappingException {
        parent.getNode(ConfigKeys.KEY_IAF_FILTER).setValue(comparatorName);
        if (template instanceof ItemType) {
            parent.getNode(ConfigKeys.KEY_IAF_TYPE).setValue(((ItemType) template).getId());
        } else if (template instanceof ItemTypeEx) {
            parent.getNode(ConfigKeys.KEY_IAF_TYPE).setValue(template.toString());
        } else if (template instanceof ItemStackSnapshot) {
            parent.getNode(ConfigKeys.KEY_IAF_ITEM).setValue(TypeTokens.ITEM_SNAPSHOT_TOKEN, (ItemStackSnapshot)template);
        } else {
            parent.getNode("hint").setValue("Can't serialize custom Applicability Filter");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ApplicabilityFilters<?> that = (ApplicabilityFilters<?>) o;
        if (!comparatorName.equalsIgnoreCase(that.comparatorName))
            return false;
        if( (template instanceof ItemStackSnapshot && that.template instanceof ItemStackSnapshot) ||
            (template instanceof ItemType && that.template instanceof ItemType) ) {
            return template.equals(that.template);
        } else if ( template instanceof ItemStackSnapshot || that.template instanceof ItemStackSnapshot ) {
            return false;
        } else if ( template instanceof ItemTypeEx ) {
            return ((ItemTypeEx)template).equals( that.template );
        } else if ( that.template instanceof ItemTypeEx ) {
            return ((ItemTypeEx)that.template).equals( template );
        } else return Objects.equal(template, that.template);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(comparatorName, template);
    }
}
