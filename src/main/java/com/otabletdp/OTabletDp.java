package com.otabletdp;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OTabletDp implements ModInitializer {
    public static final String ID = "o_tablet_dp";
    public static final Logger LOGGER = LoggerFactory.getLogger("O-Tablet-dp");

    @Override
    public void onInitialize() {
        ModContent.init();
        LOGGER.debug("O-Tablet-dp // {}", Credits.tag());

        // creative inventory
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(entries -> {
            entries.accept(ModContent.TABLET);
            entries.accept(ModContent.WRENCH);
            entries.accept(ModContent.GENERATOR_ITEM);
        });

        // blackout bookkeeping
        ServerLifecycleEvents.SERVER_STARTED.register(server -> Blackout.reset());
        ServerTickEvents.END_SERVER_TICK.register(Blackout::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> Blackout.onJoin(handler.player));

        // red wool "Drop Body" item (given by the datapack when a body is picked up)
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getItemInHand(hand);
            if (DropWool.isDropper(stack)) {
                DropWool.drop(player, world.isClientSide());
                return InteractionResultHolder.success(stack);
            }
            return InteractionResultHolder.pass(stack);
        });
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (DropWool.isDropper(player.getItemInHand(hand))) {
                DropWool.drop(player, world.isClientSide());
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });

        // admin test command: /otablet blackout on|off
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("otablet")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("blackout")
                                .then(Commands.literal("on").executes(ctx -> {
                                    boolean ok = Blackout.start(ctx.getSource().getServer(), null);
                                    ctx.getSource().sendSuccess(() -> Component.literal(ok
                                            ? "Blackout started (everyone is blind)."
                                            : "A blackout is already active."), false);
                                    return 1;
                                }))
                                .then(Commands.literal("off").executes(ctx -> {
                                    Blackout.fix(ctx.getSource().getServer(), null);
                                    ctx.getSource().sendSuccess(() -> Component.literal("Blackout cleared."), false);
                                    return 1;
                                })))));
    }
}
