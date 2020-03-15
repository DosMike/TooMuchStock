package de.dosmike.sponge.toomuchstock.utils;

import org.spongepowered.api.Sponge;
import org.spongepowered.api.data.DataContainer;
import org.spongepowered.api.data.DataQuery;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.item.ItemType;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;

import java.util.Optional;

/** WE ARE STILL NOT IN 1.13! META VALUES ARE USED - NOBODY LIKES THAT BUT HERE WE GO */
public class ItemTypeEx {

    private ItemType itemType;
    private static final DataQuery dqStackSize = DataQuery.of("Count"); //not interesting for filtering

    //used for variants up to mc 1.12.2
    private static final DataQuery dqDamageMeta = DataQuery.of("UnsafeDamage");
    public static final int META_IGNORE = 32767;
    private int meta = META_IGNORE;

    public ItemTypeEx(String definition) {
        int lioc = definition.lastIndexOf(':');
        if (lioc < 0) {
            // no namespace - try minecraft:, otherwise assume oredict
            Optional<ItemType> typeTest = Sponge.getRegistry().getType(ItemType.class, definition);
            if (typeTest.isPresent()) {
                this.itemType = typeTest.get();
            } else {
                throw new IllegalArgumentException("No item with type "+definition+" found (OreDict is not supported)");
            }
        } else if (definition.indexOf(':') != lioc) { //two colons
            String intpart = definition.substring(lioc+1);
            String typePart = definition.substring(0, lioc);
            this.itemType = Sponge.getRegistry().getType(ItemType.class, typePart).orElseThrow(()->new IllegalArgumentException("Could not find item type for "+definition));
            if (!intpart.equals("*")) {
                try {
                    meta = Integer.parseInt(intpart);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Meta value not * or integer for "+definition);
                }
            }
        }
    }
    /** meta is forge magic value ignore */
    public ItemTypeEx(ItemType type) {
        this.itemType = type;
    }
    /** @throws IllegalArgumentException exception if item type supports damage */
    public ItemTypeEx(ItemType type, int meta) {
        this.itemType = type;
        if (!type.getTemplate().supports(Keys.ITEM_DURABILITY) && meta != META_IGNORE) throw new IllegalArgumentException(type.getId()+" does not support meta values");
        this.meta = meta;
    }
    /** extract type and meta from an item stack */
    public ItemTypeEx(ItemStackSnapshot item) {
        this.itemType = item.getType();
        if (!item.supports(Keys.ITEM_DURABILITY)) {
            meta = item.toContainer().getInt(dqDamageMeta).orElse(0);
        }
    }

    public ItemType getType() {
        return itemType;
    }
    public int getMeta() {
        return meta;
    }

    public boolean equals(ItemTypeEx type) {
        if (type == null) return false;
        return itemType.equals(type.itemType) &&
                (meta == META_IGNORE || type.meta == META_IGNORE || meta == type.meta);
    }
    public boolean equals(ItemType type) {
        if (type == null) return false;
        return itemType.equals(type) &&
                (meta == META_IGNORE || type.getTemplate().supports(Keys.ITEM_DURABILITY));
    }
    public boolean equals(ItemType type, int meta) {
        if (type == null) return false;
        return itemType.equals(type) &&
                (this.meta == META_IGNORE || meta == META_IGNORE || this.meta == meta);
    }
    public boolean equals(ItemStackSnapshot item) {
        if (item == null) return false;
        int othermeta = !item.supports(Keys.ITEM_DURABILITY) ? item.toContainer().getInt(dqDamageMeta).orElse(0) : META_IGNORE;
        return itemType.equals(item.getType()) &&
                (this.meta == META_IGNORE || othermeta == META_IGNORE || this.meta == othermeta);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(itemType.getId());
        if (!itemType.getTemplate().supports(Keys.ITEM_DURABILITY)) {
            sb.append(':');
            if (meta == META_IGNORE)
                sb.append('*');
            else
                sb.append(meta);
        }
        return sb.toString();
    }

    public ItemStackSnapshot getTemplate() {
        if (meta == META_IGNORE)
            return itemType.getTemplate();

        DataContainer container = itemType.getTemplate().toContainer();
        container.set(dqDamageMeta, meta);
        return ItemStack.builder()
                .fromContainer(container)
                .build().createSnapshot();
    }

}
