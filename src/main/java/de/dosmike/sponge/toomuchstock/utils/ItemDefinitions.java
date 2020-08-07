package de.dosmike.sponge.toomuchstock.utils;

import de.dosmike.sponge.toomuchstock.ConfigKeys;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import org.spongepowered.api.util.TypeTokens;

import java.util.HashMap;

public class ItemDefinitions extends HashMap<String, ApplicabilityFilters<?>> {

    /** parses the definition and add it with the specified key string */
    public void fromConfiguration(String name, ConfigurationNode definition) throws ObjectMappingException {

        if (!name.startsWith("$"))
            throw new ObjectMappingException("Names of item definitions have to start with $");
        String nbtrule = definition.getNode(ConfigKeys.KEY_IAF_FILTER).getString("<UNSET>");
        if (ApplicabilityFilters.DEFAULT_FILTER_ITEMNBT.equalsIgnoreCase(nbtrule)) {
            put(name, ApplicabilityFilters.generateContainerExactEquals(definition.getNode(ConfigKeys.KEY_IAF_ITEM).getValue(TypeTokens.ITEM_SNAPSHOT_TOKEN)));
        } else {
            String typedef = definition.getNode(ConfigKeys.KEY_IAF_TYPE).getString();
            if (typedef == null)
                throw new ObjectMappingException("Item Type was not specified for "+name);
            ItemTypeEx type;
            try {
                type = new ItemTypeEx(typedef);
            } catch (IllegalArgumentException e) {
                throw new ObjectMappingException(e);
            }
            if (ApplicabilityFilters.DEFAULT_FILTER_ITEMTYPEMETA.endsWith(nbtrule)) {
                if (type.getMeta() == ItemTypeEx.META_IGNORE) {
                    put(name, ApplicabilityFilters.generateItemTypeEquals(type.getType()));
                } else {
                    put(name, ApplicabilityFilters.generateItemTypeMetaEquals(type));
                }
            }if (ApplicabilityFilters.DEFAULT_FILTER_ITEMTYPE.equalsIgnoreCase(nbtrule)) {
                put(name, ApplicabilityFilters.generateItemTypeEquals(type.getType()));
            } else {
                throw new ObjectMappingException("Invalid filter "+nbtrule+" for "+type+" ("+name+")");
            }
        }

    }

    public void toConfiguration(ConfigurationNode parent) throws ObjectMappingException {
        for (Entry<String, ApplicabilityFilters<?>> entry : entrySet()) {
            if (!entry.getKey().startsWith("$")) continue; //don't (re-)save item type definitions here
            try {
                entry.getValue().toConfiguration(parent.getNode(entry.getKey()));
            } catch (Exception e) {
                System.err.println("Could not save item "+entry.getKey()+": ");
                e.printStackTrace();
            }
        }
    }

}
