package com.otabletdp;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
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
            entries.accept(ModContent.LOCATION_MARKER_ITEM);
            entries.accept(ModContent.SCREWDRIVER);
            entries.accept(ModContent.POISON_BOTTLE);
            entries.accept(ModContent.WOODEN_HAMMER);
            entries.accept(ModContent.BLANK_KEY);
            entries.accept(ModContent.LOCK_PICK);
            entries.accept(ModContent.DORM_DOOR_ITEM);
            entries.accept(ModContent.MASTERMIND_TABLET);
            entries.accept(ModContent.TRIAL_TELEPORTER_ITEM);
        });

        // blackout bookkeeping
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            Blackout.reset();
            Guide.reset();
            Poison.reset();
            LockPick.reset();
            Trial.reset();
            ModData.load(server);
        });
        ServerTickEvents.END_SERVER_TICK.register(Blackout::tick);
        ServerTickEvents.END_SERVER_TICK.register(Guide::tick);
        ServerTickEvents.END_SERVER_TICK.register(Poison::tick);
        ServerTickEvents.END_SERVER_TICK.register(Trapdoors::tick);
        ServerTickEvents.END_SERVER_TICK.register(Keys::tick);
        ServerTickEvents.END_SERVER_TICK.register(LockPick::tick);
        ServerTickEvents.END_SERVER_TICK.register(Trial::tick);

        // a player kill makes the killer the Blackened
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer victim && source.getEntity() instanceof ServerPlayer killer
                    && killer != victim && victim.getServer() != null
                    && Game.isActive(victim.getServer())
                    && Ultimates.of(victim) >= 1 && Ultimates.of(killer) >= 1) {
                Blackened.mark(killer, victim.getName().getString());
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> Blackout.onJoin(handler.player));

        // red wool "Drop Body" item (given by the datapack when a body is picked up)
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getItemInHand(hand);
            String trialKind = Trial.kindOf(stack);
            if (!trialKind.isEmpty()) {
                if (!world.isClientSide() && player instanceof ServerPlayer sp) {
                    Trial.useItem(sp, trialKind);
                }
                return InteractionResultHolder.success(stack);
            }
            if (hand == InteractionHand.MAIN_HAND && player.isShiftKeyDown()
                    && Poison.canLace(stack, player.getOffhandItem())) {
                if (!world.isClientSide() && player instanceof ServerPlayer sp) {
                    Poison.lace(sp);
                }
                return InteractionResultHolder.success(stack);
            }
            if (DropWool.isDropper(stack)) {
                DropWool.drop(player, world.isClientSide());
                return InteractionResultHolder.success(stack);
            }
            return InteractionResultHolder.pass(stack);
        });
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            String trialKind = Trial.kindOf(player.getItemInHand(hand));
            if (!trialKind.isEmpty()) {
                if (!world.isClientSide() && player instanceof ServerPlayer sp) {
                    Trial.useItem(sp, trialKind);
                }
                return InteractionResult.SUCCESS;
            }
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
                        .then(Commands.literal("marker")
                                .then(Commands.literal("name")
                                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                                    String name = StringArgumentType.getString(ctx, "name");
                                                    HitResult hit = p.pick(8.0, 1.0f, false);
                                                    if (hit instanceof BlockHitResult bhr
                                                            && p.level().getBlockState(bhr.getBlockPos()).is(ModContent.LOCATION_MARKER)) {
                                                        ModData.renameMarker(p.level(), bhr.getBlockPos(), name);
                                                        ctx.getSource().sendSuccess(() -> Component.literal("Marker renamed to " + name), false);
                                                        return 1;
                                                    }
                                                    ctx.getSource().sendFailure(Component.literal("Look at a location marker block first."));
                                                    return 0;
                                                }))))
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
