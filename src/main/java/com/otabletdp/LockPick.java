package com.otabletdp;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;

/** The lock pick minigame: like the generator repair, but smaller and easier. */
public final class LockPick {
    private LockPick() {}

    private static final int SECONDS = 15;
    private static final int PANELS = 7; // one row of 7 inside a 9x3 menu

    private static final class Session {
        final String dim;
        final BlockPos base;
        final long end;
        final boolean[] green = new boolean[PANELS];

        Session(String dim, BlockPos base, long end) {
            this.dim = dim;
            this.base = base;
            this.end = end;
        }
    }

    private static final Map<UUID, Session> sessions = new HashMap<>();

    public static void reset() {
        sessions.clear();
    }

    public static void start(ServerPlayer player, Level level, BlockPos base) {
        UUID id = player.getUUID();
        Session session = sessions.get(id);
        if (session == null) {
            session = new Session(level.dimension().location().toString(), base,
                    player.getServer().getTickCount() + SECONDS * 20L);
            sessions.put(id, session);
            Util.say(player, Util.txt("You have " + SECONDS + " seconds to pick the lock!", ChatFormatting.YELLOW));
        }
        open(player, session);
    }

    private static void open(ServerPlayer player, Session session) {
        Gui gui = new Gui("lockpick", 3, Component.literal("Lock Pick"));
        Util.border(gui, 3, Items.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < PANELS; i++) {
            int slot = 10 + i;
            if (session.green[i]) {
                gui.set(slot, Util.named(Items.GREEN_STAINED_GLASS_PANE, Util.txt("Set", ChatFormatting.GREEN), null));
            } else {
                final int panel = i;
                gui.set(slot, Util.named(Items.RED_STAINED_GLASS_PANE, Util.txt("Pin", ChatFormatting.RED), null),
                        () -> onClick(gui, player, session, panel));
            }
        }
        gui.open(player);
    }

    private static void onClick(Gui gui, ServerPlayer player, Session session, int panel) {
        if (session.green[panel]) return;
        session.green[panel] = true;
        gui.set(10 + panel, Util.named(Items.GREEN_STAINED_GLASS_PANE, Util.txt("Set", ChatFormatting.GREEN), null));
        Util.sound(player, "minecraft:block.note_block.hat", 1.5f);
        for (boolean done : session.green) {
            if (!done) return;
        }
        success(player, session);
    }

    private static void success(ServerPlayer player, Session session) {
        sessions.remove(player.getUUID());
        closeGui(player);
        MinecraftServer server = player.getServer();
        ServerLevel level = Util.level(server, session.dim);
        if (level != null) {
            BlockState state = level.getBlockState(session.base);
            if (state.getBlock() instanceof DoorBlock door) {
                door.setOpen(player, level, state, session.base, true);
            }
        }
        ItemStack held = player.getMainHandItem();
        if (held.is(ModContent.LOCK_PICK)) {
            held.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        }
        Util.say(player, Util.txt("Click! The lock gave way.", ChatFormatting.GREEN));
    }

    private static void closeGui(ServerPlayer player) {
        if (player.containerMenu instanceof Gui.Menu menu && menu.guiId().equals("lockpick")) {
            player.closeContainer();
        }
    }

    public static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        Iterator<Map.Entry<UUID, Session>> it = sessions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Session> entry = it.next();
            if (now >= entry.getValue().end) {
                it.remove();
                ServerPlayer p = server.getPlayerList().getPlayer(entry.getKey());
                if (p != null) {
                    closeGui(p);
                    Util.say(p, Util.txt("The pick slipped. Try again.", ChatFormatting.RED));
                }
            }
        }
    }
}
