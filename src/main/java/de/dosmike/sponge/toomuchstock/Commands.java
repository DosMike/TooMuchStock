package de.dosmike.sponge.toomuchstock;

import de.dosmike.sponge.toomuchstock.maths.ItemTracker;
import de.dosmike.sponge.toomuchstock.utils.ApplicabilityFilters;
import de.dosmike.sponge.toomuchstock.utils.ItemTypeEx;
import de.dosmike.sponge.toomuchstock.utils.Stonks;
import org.spongepowered.api.CatalogType;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.args.GenericArguments;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.data.type.HandTypes;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.item.ItemType;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class Commands {

    static CommandSpec subcmdRegisterItem() {
        Map<String,String> filterType = new HashMap<>();
        filterType.put("type", "type");
        filterType.put("typemeta", "typemeta");
        filterType.put("exact", "exact");
        return CommandSpec.builder()
                .permission("toomuchstock.command.define")
                .arguments(GenericArguments.choices(Text.of("filter"), filterType, false, false),
                        GenericArguments.remainingJoinedStrings(Text.of("name")))
                .executor((src, args)->{
                    if (!(src instanceof Player))
                        throw new CommandException(Text.of(TextColors.RED, "Player command"));
                    Player player = (Player) src;
                    ItemStack stack = player.getItemInHand(HandTypes.MAIN_HAND).orElseThrow(()->new CommandException(Text.of(TextColors.RED, "You need to hold the item you want to add to the definitions")));

                    ApplicabilityFilters<?> filter;
                    switch(args.<String>getOne("filter").get()) {
                        case "type": {
                            filter = ApplicabilityFilters.generateItemTypeEquals(stack.getType());
                            break;
                        }
                        case "typemeta": {
                            filter = ApplicabilityFilters.generateItemTypeMetaEquals(new ItemTypeEx(stack.createSnapshot()));
                            break;
                        }
                        case "exact": {
                            filter = ApplicabilityFilters.generateContainerExactEquals(stack.createSnapshot());
                            break;
                        }
                        default:
                            throw new CommandException(Text.of("Missing Filtertype"));
                    }
                    String name = args.<String>getOne("name").get();
                    if (name.isEmpty())
                        throw new CommandException(Text.of(TextColors.RED, "You have to specify a name"));
                    if (!name.startsWith("$"))
                        name = "$"+name;
                    if (TooMuchStock.getItemDefinitionTable().containsKey(name))
                        throw new CommandException(Text.of(TextColors.RED, "There's already a definition with this name"));

                    TooMuchStock.getItemDefinitionTable().put(name, filter);
                    src.sendMessage(Text.of(TextColors.YELLOW, "Added item "+name+" "+filter.toString()));
                    try {
                        TooMuchStock.getInstance().saveConfigs();
                        src.sendMessage(Text.of(TextColors.GREEN, "Configs were re-saved"));
                        return CommandResult.success();
                    } catch (Exception e) {
                        e.printStackTrace();
                        throw new CommandException(Text.of(TextColors.RED, "Something went wrong: "+e.getMessage()+"\n Please check the logs!"));
                    }
                })
                .build();
    }

    static CommandSpec subcmdReload() {
        return CommandSpec.builder()
                .permission("toomuchstock.command.reload")
                .arguments(GenericArguments.flags().flag("-hard").buildWith(GenericArguments.none()))
                .executor(((src, args) -> {
                    try {
                        TooMuchStock.getInstance().loadConfigs(args.hasAny("-hard"));
                        src.sendMessage(Text.of(TextColors.GREEN, "Reload complete"));
                        return CommandResult.success();
                    } catch (Exception e) {
                        e.printStackTrace();
                        throw new CommandException(Text.of(TextColors.RED, "Something went wrong: "+e.getMessage()+"\n Please check the logs!"));
                    }
                }))
                .build();
    }

    static CommandSpec subcmdStonks() {
        return CommandSpec.builder()
                .permission("toomuchstock.command.stonks")
                .arguments(GenericArguments.optional(GenericArguments.choices(Text.of("item"), ()->{
                    List<String> candidates = Sponge.getGame().getRegistry().getAllOf(ItemType.class).stream().map(CatalogType::getId).collect(Collectors.toList());
                    candidates.addAll(TooMuchStock.getItemDefinitionTable().keySet());
                    return candidates;
                }, (s)->s, false)))
                .executor(((src, args) -> {
                    ItemStackSnapshot item = null;
                    String name = null;
                    if (args.hasAny("item")) {
                        String s = args.<String>getOne("item").get();
                        if (s.startsWith("$")) {
                            ApplicabilityFilters<?> filter = TooMuchStock.getItemDefinitionTable().get(s);
                            if (filter == null)
                                throw new CommandException(Text.of(TextColors.RED, "No item definition for ", TextColors.RESET, s));
                            item = filter.generateTemplate().orElseThrow(()->new CommandException(Text.of(TextColors.RED, "Definition does not provide item")));
                        } else {
                            item = new ItemTypeEx(s).getTemplate();
                        }
                        name = s;
                    } else if (src instanceof Player) {
                        ItemStack tmp = ((Player) src).getItemInHand(HandTypes.MAIN_HAND)
                                .orElseGet(()->((Player) src).getItemInHand(HandTypes.OFF_HAND)
                                .orElse(null));
                        if (tmp != null) {
                            item = tmp.createSnapshot();
                            name = item.get(Keys.DISPLAY_NAME).orElse(Text.of(item.getTranslation().get())).toPlain();
                        }
                    }
                    if (item == null) {
                        if (src instanceof Player)
                            throw new CommandException(Text.of(TextColors.RED, "Please specify or hold the item to check"));
                        else //console can't hold items :)
                            throw new CommandException(Text.of(TextColors.RED, "Please specify the item to check"));
                    }
                    Optional<Stonks> stonks;
                    src.sendMessage(Text.of(TextColors.AQUA, "=== STONKS for ", name, " ==="));
                    stonks = TooMuchStock.getPriceCalculator().getGlobalTracker(item).map(ItemTracker::getStonks);
                    if (stonks.isPresent()) {
                        src.sendMessage(Text.of("Global price fluctuation:"));
                        stonks.get().print(src);
                    } else {
                        src.sendMessage(Text.of(TextColors.YELLOW, "There's currently no global history"));
                    }
                    if (src instanceof Player) {
                        stonks = TooMuchStock.getPriceCalculator().getPlayerTracker(((Player) src).getUniqueId(), item).map(ItemTracker::getStonks);
                        if (stonks.isPresent()) {
                            src.sendMessage(Text.of(Text.NEW_LINE, "Personal price fluctuation:"));
                            stonks.get().print(src);
                        } else {
                            src.sendMessage(Text.of(TextColors.YELLOW, "There's currently no personal history"));
                        }
                        src.sendMessage(Text.of(TextColors.YELLOW, "Please keep in mind that shops might use their own history as well"));
                    } else {
                        src.sendMessage(Text.of(TextColors.YELLOW, "Please keep in mind that shops and players might use their own history as well"));
                    }
                    return CommandResult.success();
                }))
                .build();
    }

    public static void register(TooMuchStock instance) {
        Sponge.getCommandManager().register(instance, CommandSpec.builder()
                .child(subcmdRegisterItem(), "define", "register")
                .child(subcmdReload(), "reload")
                .child(subcmdStonks(), "history", "stonks")
                .build(), "toomuchstock", "tms");
    }

}
