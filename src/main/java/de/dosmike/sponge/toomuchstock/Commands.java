package de.dosmike.sponge.toomuchstock;

import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.args.GenericArguments;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.data.type.HandTypes;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;

import java.util.HashMap;
import java.util.Map;

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

                    return CommandResult.success();
                })
                .build();
    }

    public static void register(TooMuchStock instance) {
        Sponge.getCommandManager().register(instance, CommandSpec.builder()
                .child(subcmdRegisterItem(), "define", "register")
                .build(), "toomuchstock", "tms");
    }

}
