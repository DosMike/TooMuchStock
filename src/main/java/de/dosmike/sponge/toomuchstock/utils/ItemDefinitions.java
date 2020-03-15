package de.dosmike.sponge.toomuchstock.utils;

import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import org.spongepowered.api.util.TypeTokens;

import java.util.HashMap;

public class ItemDefinitions extends HashMap<String, ApplicabilityFilters<?>> {

    /** parses the definition and add it with the specified key string */
    public void fromConfiguration(String name, ConfigurationNode definition) throws ObjectMappingException {

        if (!name.startsWith("$"))
            throw new ObjectMappingException("Names of item definitions have to start with $");
        String nbtrule = definition.getNode("filter").getString("<UNSET>");
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
            if ("type".equalsIgnoreCase(nbtrule)) {
                if (type.getMeta() == ItemTypeEx.META_IGNORE) {
                    put(name, ApplicabilityFilters.generateItemTypeEquals(type.getType()));
                } else {
                    put(name, ApplicabilityFilters.generateItemTypeMetaEquals(type));
                }
            } else {
                throw new ObjectMappingException("Invalid filter "+nbtrule+" for "+type+" ("+name+")");
            }
        }

    }

    public void toConfiguration(ConfigurationNode parent) throws ObjectMappingException {
        for (Entry<String, ApplicabilityFilters<?>> entry : entrySet()) {
            try {
                entry.getValue().toConfiguration(parent.getNode(entry.getKey()));
            } catch (Exception e) {
                System.err.println("Could not save item "+entry.getKey()+": ");
                e.printStackTrace();
            }
        }
    }

}
