package com.otabletdp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;

/** Everything about the blackout: starting it, the blindness, and the repair minigame. */
public final class Blackout {
    private Blackout() {}

    /** Seconds a player gets for one repair attempt. */
    private static final int ATTEMPT_SECONDS = 30;
    /** Our blindness is tagged with this amplifier so we never remove someone else's blindness. */
    private static final int MARK = 9;
    /** Inside the 9x6 border there are 4 rows x 7 columns. */
    private static final int PANELS = 28;
    private static final Set<Integer> ANNOUNCE = Set.of(30, 25, 20, 15, 10, 5, 4, 3, 2, 1);

    private static boolean active = false;
    private static UUID causer = null;
    private static final Map<UUID, Attempt> attempts = new HashMap<>();
    private static final Set<UUID> lockedOut = new HashSet<>();

    private static final class Attempt {
        final boolean[] green = new boolean[PANELS];
        final long endTick;
        int lastRemaining = -1;

        Attempt(long endTick) {
            this.endTick = endTick;
        }
    }

    public static boolean isActive() {
        return active;
    }

    public static void reset() {
        active = false;
        causer = null;
        attempts.clear();
        lockedOut.clear();
    }

    // ------------------------------------------------------------------ start

    /** @param starter the player who caused it (keeps their sight), or null for "everyone is blind". */
    public static boolean start(MinecraftServer server, ServerPlayer starter) {
        if (active) return false;
        active = true;
        causer = starter == null ? null : starter.getUUID();
        attempts.clear();
        lockedOut.clear();
        applyEffects(server);

        server.getPlayerList().broadcastSystemMessage(
                Util.txt("[Blackout] The lights went out! Find the generator and repair it.", ChatFormatting.RED), false);
        Util.cmd(server, "title @a times 10 60 20");
        Util.cmd(server, "title @a subtitle {\"text\":\"Find the generator and repair it.\",\"color\":\"gray\"}");
        Util.cmd(server, "title @a title {\"text\":\"BLACKOUT\",\"color\":\"red\",\"bold\":true}");
        Util.cmd(server, "execute as @a at @s run playsound minecraft:block.beacon.deactivate master @s ~ ~ ~ 1 0.6");
        return true;
    }

    // ------------------------------------------------------------------ repair

    /** Called when someone right-clicks the generator during a blackout. */
    public static void tryFix(ServerPlayer player) {
        UUID id = player.getUUID();
        if (lockedOut.contains(id)) {
            Util.say(player, Util.txt("You ran out of time. Someone else has to repair the generator.",
                    ChatFormatting.RED));
            return;
        }
        Attempt attempt = attempts.get(id);
        if (attempt == null) {
            attempt = new Attempt(player.getServer().getTickCount() + ATTEMPT_SECONDS * 20L);
            if (Ultimates.of(player) == Ultimates.LUCKY_STUDENT) {
                presetLucky(attempt);
            }
            attempts.put(id, attempt);
            Util.say(player, Util.txt("You have " + ATTEMPT_SECONDS + " seconds to repair the generator!",
                    ChatFormatting.YELLOW));
        }
        openFix(player, attempt);
    }

    /** Lucky Student: 25% of the panels are already repaired. */
    private static void presetLucky(Attempt attempt) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < PANELS; i++) order.add(i);
        Collections.shuffle(order, new Random());
        int count = Math.round(PANELS * 0.25f);
        for (int i = 0; i < count; i++) {
            attempt.green[order.get(i)] = true;
        }
    }

    private static int slotOf(int panel) {
        return (1 + panel / 7) * 9 + 1 + panel % 7;
    }

    private static void openFix(ServerPlayer player, Attempt attempt) {
        Gui gui = new Gui("fix", 6, Component.literal("Generator Repair"));
        Util.border(gui, 6, Items.BLACK_STAINED_GLASS_PANE);

        for (int panel = 0; panel < PANELS; panel++) {
            int slot = slotOf(panel);
            if (attempt.green[panel]) {
                gui.set(slot, repaired());
            } else {
                final int p = panel;
                gui.set(slot, broken(), () -> onPanelClick(gui, player, attempt, p));
            }
        }
        gui.open(player);
    }

    private static net.minecraft.world.item.ItemStack broken() {
        return Util.named(Items.RED_STAINED_GLASS_PANE, Util.txt("Broken circuit", ChatFormatting.RED), null);
    }

    private static net.minecraft.world.item.ItemStack repaired() {
        return Util.named(Items.GREEN_STAINED_GLASS_PANE, Util.txt("Repaired", ChatFormatting.GREEN), null);
    }

    private static void onPanelClick(Gui gui, ServerPlayer player, Attempt attempt, int panel) {
        if (!active || attempt.green[panel]) return;
        attempt.green[panel] = true;
        gui.set(slotOf(panel), repaired());
        Util.sound(player, "minecraft:block.note_block.bell", 1.5f);

        for (boolean done : attempt.green) {
            if (!done) return;
        }
        fix(player.getServer(), player);
    }

    /** End the blackout. @param fixer who repaired it, or null (admin command). */
    public static void fix(MinecraftServer server, ServerPlayer fixer) {
        boolean wasActive = active;
        active = false;
        causer = null;
        attempts.clear();
        lockedOut.clear();

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            removeOurBlindness(p);
            closeFix(p);
        }
        if (!wasActive) return;

        String who = fixer == null ? "Someone" : fixer.getName().getString();
        server.getPlayerList().broadcastSystemMessage(
                Util.txt("[Blackout] " + who + " repaired the generator. The lights are back!", ChatFormatting.GREEN),
                false);
        Util.cmd(server, "title @a times 10 50 20");
        Util.cmd(server, "title @a subtitle {\"text\":\"The generator has been repaired.\",\"color\":\"gray\"}");
        Util.cmd(server, "title @a title {\"text\":\"Lights Restored\",\"color\":\"green\",\"bold\":true}");
        Util.cmd(server, "execute as @a at @s run playsound minecraft:block.beacon.activate master @s ~ ~ ~ 1 1");
    }

    private static void closeFix(ServerPlayer player) {
        if (player.containerMenu instanceof Gui.Menu menu && menu.guiId().equals("fix")) {
            player.closeContainer();
        }
    }

    // ------------------------------------------------------------------ ticking

    public static void tick(MinecraftServer server) {
        if (!active) return;
        long now = server.getTickCount();

        if (now % 20 == 0) {
            applyEffects(server);
        }

        Iterator<Map.Entry<UUID, Attempt>> it = attempts.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Attempt> entry = it.next();
            Attempt attempt = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            int remaining = (int) Math.ceil((attempt.endTick - now) / 20.0);

            if (remaining <= 0) {
                it.remove();
                lockedOut.add(entry.getKey());
                if (player != null) {
                    closeFix(player);
                    Util.say(player, Util.txt("Time's up! You can't try again. Someone else has to repair it.",
                            ChatFormatting.RED));
                    Util.sound(player, "minecraft:entity.villager.no", 1.0f);
                }
                continue;
            }
            if (remaining != attempt.lastRemaining) {
                attempt.lastRemaining = remaining;
                if (player != null && ANNOUNCE.contains(remaining)) {
                    ChatFormatting color = remaining <= 10 ? ChatFormatting.RED : ChatFormatting.YELLOW;
                    Util.say(player, Util.txt("Repair time left: " + remaining + "s", color));
                }
            }
        }
    }

    // ------------------------------------------------------------------ blindness

    private static void applyEffects(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            boolean exempt = p.isSpectator() || (causer != null && causer.equals(p.getUUID()));
            MobEffectInstance current = p.getEffect(MobEffects.BLINDNESS);
            boolean ours = current != null && current.getAmplifier() == MARK;
            if (exempt) {
                if (ours) p.removeEffect(MobEffects.BLINDNESS);
            } else if (!ours) {
                p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS,
                        MobEffectInstance.INFINITE_DURATION, MARK, false, false, false));
            }
        }
    }

    private static void removeOurBlindness(ServerPlayer p) {
        MobEffectInstance current = p.getEffect(MobEffects.BLINDNESS);
        if (current != null && current.getAmplifier() == MARK) {
            p.removeEffect(MobEffects.BLINDNESS);
        }
    }

    /** Stuck blindness from a previous session (server restarted mid-blackout) is cleaned up on join. */
    public static void onJoin(ServerPlayer player) {
        if (!active) {
            removeOurBlindness(player);
        }
    }
}

// ~ Othalchan ~
