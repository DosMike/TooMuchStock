package de.dosmike.sponge.toomuchstock.utils;

import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;
import org.spongepowered.api.util.TypeTokens;

import java.util.HashMap;
import java.util.function.Predicate;

public class ItemDefinitions extends HashMap<String, Predicate<ItemStackSnapshot>> {

    /** parses the definition and add it with the specified key string */
    public void fromConfiguration(String name, ConfigurationNode definition) throws ObjectMappingException {

        String nbtrule = definition.getNode("nbt").getString("IGNORE");
        if ("exact".equalsIgnoreCase(nbtrule)) {
            put(name, ApplicabilityFilters.generateContainerExactEquals(definition.getNode("item").getValue(TypeTokens.ITEM_SNAPSHOT_TOKEN)));
        } else {
            String typedef = definition.getNode("type").getString();
            if (typedef == null)
                throw new ObjectMappingException("Item Type was not specified for "+name);
            ItemTypeEx type;
            try {
                type = new ItemTypeEx(typedef);
            } catch (IllegalArgumentException e) {
                throw new ObjectMappingException(e);
            }
            if ("typemeta".equalsIgnoreCase(nbtrule)) {
                put(name, ApplicabilityFilters.generateItemTypeMetaEquals(type));
            } else if ("type".equalsIgnoreCase(nbtrule)) {
                put(name, ApplicabilityFilters.generateItemTypeEquals(type.getType()));
            }
        }

    }

}
