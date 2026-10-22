package com.otabletdp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

/** The class trial: teleport, rules from Koraidomia, 10 minute debate, then the vote. */
public final class Trial {
    private Trial() {}

    private enum Phase { NONE, INTRO, DEBATE, VOTING }

    private static final int DEBATE_SECONDS = 600;
    private static final int VOTE_SECONDS = 120;
    private static final Set<Integer> DEBATE_ANNOUNCE = Set.of(300, 60, 30, 10);
    private static final Set<Integer> VOTE_ANNOUNCE = Set.of(60, 30, 10);
    private static final String ITEM_KEY = "dr_trial_item";

    private record Scheduled(long tick, Runnable action) {}

    private static Phase phase = Phase.NONE;
    private static long phaseEnd = 0;
    private static int lastShown = -1;
    private static final Set<UUID> participants = new LinkedHashSet<>();
    private static final Map<UUID, String> names = new HashMap<>();
    private static final Set<UUID> skipVotes = new HashSet<>();
    private static final Map<UUID, UUID> votes = new HashMap<>();
    private static final List<Scheduled> queue = new ArrayList<>();

    public static boolean isRunning() {
        return phase != Phase.NONE;
    }

    public static void reset() {
        phase = Phase.NONE;
        participants.clear();
        names.clear();
        skipVotes.clear();
        votes.clear();
        queue.clear();
    }

    // ------------------------------------------------------------------ items

    public static String kindOf(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? "" : data.copyTag().getString(ITEM_KEY);
    }

    private static ItemStack makeItem(net.minecraft.world.item.Item item, String name, ChatFormatting color,
                                      String hint, String kind) {
        ItemStack stack = Util.named(item, Util.txt(name, color), List.of(Util.txt(hint, ChatFormatting.GRAY)));
        CompoundTag tag = new CompoundTag();
        tag.putString(ITEM_KEY, kind);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    private static void give(ServerPlayer p, ItemStack stack) {
        if (!p.getInventory().add(stack)) p.drop(stack, false);
    }

    private static void clearItems(ServerPlayer p) {
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            if (!kindOf(p.getInventory().getItem(i)).isEmpty()) {
                p.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
    }

    private static void clearAllItems(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) clearItems(p);
    }

    // ------------------------------------------------------------------ helpers

    private static void say(MinecraftServer server, String text) {
        Component message = Component.literal("[Koraidomia] ")
                .withStyle(style -> style.withBold(true).withColor(ChatFormatting.GOLD))
                .append(Component.literal(text).withStyle(ChatFormatting.WHITE));
        server.getPlayerList().broadcastSystemMessage(message, false);
    }

    private static List<ServerPlayer> online(MinecraftServer server) {
        List<ServerPlayer> out = new ArrayList<>();
        for (UUID id : participants) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) out.add(p);
        }
        return out;
    }

    private static void bell(MinecraftServer server, float pitch) {
        Util.cmd(server, "execute as @a at @s run playsound minecraft:block.bell.use master @s ~ ~ ~ 1 " + pitch);
    }

    private static void title(MinecraftServer server, String title, String subtitle, String color) {
        Util.cmd(server, "title @a times 10 70 20");
        Util.cmd(server, "title @a subtitle {\"text\":\"" + subtitle + "\",\"color\":\"gray\"}");
        Util.cmd(server, "title @a title {\"text\":\"" + title + "\",\"color\":\"" + color + "\",\"bold\":true}");
    }

    private static void later(long tick, Runnable action) {
        queue.add(new Scheduled(tick, action));
    }

    // ------------------------------------------------------------------ start

    /** @return null if it started, or the reason it could not. */
    public static String start(MinecraftServer server) {
        if (phase != Phase.NONE) return "A class trial is already running.";
        if (!Game.isActive(server)) return "Start the game first.";

        List<ModData.Loc> all = ModData.teleporters();
        if (all.isEmpty()) return "No Class Trial Teleporter blocks are placed.";
        String dim = all.get(0).dim();
        List<ModData.Loc> spots = new ArrayList<>();
        for (ModData.Loc l : all) {
            if (l.dim().equals(dim)) spots.add(l);
        }
        ServerLevel level = Util.level(server, dim);
        if (level == null) return "The teleporters' dimension could not be found.";

        List<ServerPlayer> players = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (Ultimates.isAlive(p) && !p.getTags().contains("mastermind")) players.add(p);
        }
        if (players.isEmpty()) return "There are no living players to put on trial.";

        reset();
        phase = Phase.INTRO;

        // everyone gets a random podium, and they all face the middle
        double cx = 0, cz = 0;
        for (ModData.Loc l : spots) {
            cx += l.x() + 0.5;
            cz += l.z() + 0.5;
        }
        cx /= spots.size();
        cz /= spots.size();

        Collections.shuffle(spots);
        Collections.shuffle(players);
        for (int i = 0; i < players.size(); i++) {
            ServerPlayer p = players.get(i);
            ModData.Loc spot = spots.get(i % spots.size());
            double x = spot.x() + 0.5;
            double z = spot.z() + 0.5;
            float yaw = (float) Math.toDegrees(Math.atan2(-(cx - x), cz - z));
            if (p.getTags().contains("carrying")) Util.fnAs(p, "dr:dropbody");
            p.teleportTo(level, x, spot.y() + 0.1875, z, yaw, 0.0f);
            participants.add(p.getUUID());
            names.put(p.getUUID(), p.getName().getString());
            clearItems(p);
        }

        long now = server.getTickCount();
        later(now + 20, () -> title(server, "CLASS TRIAL", "Take your places.", "gold"));
        later(now + 80, () -> say(server, "Ahem! Welcome to the courtroom, you lot. I'm Koraidomia, your host for today's trial!"));
        later(now + 200, () -> say(server, "Rule one: one of you is the Blackened, the one who did the deed. Your job is to figure out who."));
        later(now + 320, () -> say(server, "Rule two: when the debate ends, everybody votes for one person. Whoever gets the most votes is the accused."));
        later(now + 440, () -> say(server, "Rule three: name the right culprit and only the Blackened gets punished. Name the wrong one and the Blackened walks free, while everybody else gets punished!"));
        later(now + 560, () -> say(server, "Rule four: you get 10 minutes of debate. If all of you agree to skip it, we go straight to the vote."));
        later(now + 760, () -> startDebate(server));
        return null;
    }

    private static void startDebate(MinecraftServer server) {
        phase = Phase.DEBATE;
        phaseEnd = server.getTickCount() + DEBATE_SECONDS * 20L;
        lastShown = -1;
        skipVotes.clear();
        for (ServerPlayer p : online(server)) {
            give(p, makeItem(Items.YELLOW_WOOL, "Skip Debate", ChatFormatting.YELLOW,
                    "Right-click to vote yes to skip the debate.", "skip"));
        }
        bell(server, 1.0f);
        title(server, "THE CLASS TRIAL HAS STARTED", "10 minutes of debate. Find the Blackened!", "red");
        say(server, "The class trial has started! You have 10 minutes. Let the debate begin!");
    }

    // ------------------------------------------------------------------ debate / skip

    public static void useItem(ServerPlayer player, String kind) {
        if (kind.equals("skip")) {
            useSkip(player);
        } else if (kind.equals("vote")) {
            if (phase == Phase.VOTING && participants.contains(player.getUUID())) {
                openVote(player);
            }
        }
    }

    private static void useSkip(ServerPlayer player) {
        if (phase != Phase.DEBATE || !participants.contains(player.getUUID())) return;
        MinecraftServer server = player.getServer();
        if (!skipVotes.add(player.getUUID())) {
            Util.say(player, Util.txt("You already voted to skip.", ChatFormatting.GRAY));
            return;
        }
        int total = online(server).size();
        say(server, player.getName().getString() + " wants to skip the debate. (" + skipVotes.size() + "/" + total + ")");
        if (skipVotes.size() >= total) {
            startVoting(server, "Everybody agreed to skip the debate.");
        }
    }

    // ------------------------------------------------------------------ voting

    private static void startVoting(MinecraftServer server, String reason) {
        phase = Phase.VOTING;
        phaseEnd = server.getTickCount() + VOTE_SECONDS * 20L;
        lastShown = -1;
        votes.clear();
        for (ServerPlayer p : online(server)) {
            clearItems(p);
            give(p, makeItem(Items.BLACK_WOOL, "Voting Block", ChatFormatting.DARK_GRAY,
                    "Right-click to vote for the culprit.", "vote"));
        }
        bell(server, 0.8f);
        title(server, "VOTING TIME", "Right-click the black wool to vote.", "dark_red");
        say(server, reason + " It's time to vote! You have " + VOTE_SECONDS + " seconds.");
    }

    private static void openVote(ServerPlayer voter) {
        MinecraftServer server = voter.getServer();
        Gui gui = new Gui("vote", 6, Component.literal("Who is the culprit?"));
        Util.border(gui, 6, Items.BLACK_STAINED_GLASS_PANE);

        List<ServerPlayer> candidates = new ArrayList<>();
        for (ServerPlayer p : online(server)) {
            if (!p.getUUID().equals(voter.getUUID())) candidates.add(p);
        }
        candidates.sort(Comparator.comparing(p -> p.getName().getString().toLowerCase()));

        int n = 0;
        for (ServerPlayer candidate : candidates) {
            if (n >= 28) break;
            int slot = (1 + n / 7) * 9 + 1 + n % 7;
            List<Component> lore = List.of(Util.txt("Click to vote for this player.", ChatFormatting.GRAY));
            gui.set(slot, Util.head(candidate, Util.txt(candidate.getName().getString(), ChatFormatting.WHITE), lore),
                    () -> castVote(voter, candidate));
            n++;
        }
        gui.open(voter);
    }

    private static void castVote(ServerPlayer voter, ServerPlayer target) {
        if (phase != Phase.VOTING) return;
        if (!participants.contains(voter.getUUID()) || !participants.contains(target.getUUID())) return;
        votes.put(voter.getUUID(), target.getUUID());
        voter.closeContainer();
        Util.say(voter, Util.txt("You voted for " + target.getName().getString()
                + ". (Right-click the black wool again to change your vote.)", ChatFormatting.GRAY));
        MinecraftServer server = voter.getServer();
        if (votes.size() >= online(server).size()) {
            finish(server);
        }
    }

    private static void finish(MinecraftServer server) {
        Map<UUID, Integer> counts = new HashMap<>();
        for (UUID target : votes.values()) counts.merge(target, 1, Integer::sum);

        say(server, "The votes are in!");
        int best = 0;
        for (Map.Entry<UUID, Integer> e : counts.entrySet()) {
            best = Math.max(best, e.getValue());
            say(server, "  " + names.getOrDefault(e.getKey(), "?") + ": " + e.getValue() + " vote(s)");
        }
        UUID accused = null;
        int atTop = 0;
        for (Map.Entry<UUID, Integer> e : counts.entrySet()) {
            if (e.getValue() == best) {
                atTop++;
                accused = e.getKey();
            }
        }
        if (counts.isEmpty()) {
            say(server, "Nobody voted at all. How disappointing.");
            accused = null;
        } else if (atTop > 1) {
            say(server, "It's a tie, so nobody got the majority!");
            accused = null;
        } else {
            say(server, "The accused is " + names.getOrDefault(accused, "?") + "!");
        }

        Set<UUID> blackened = new HashSet<>();
        List<String> blackenedNames = new ArrayList<>();
        for (ServerPlayer p : online(server)) {
            if (p.getTags().contains(Blackened.TAG)) {
                blackened.add(p.getUUID());
                blackenedNames.add(p.getName().getString());
            }
        }
        if (blackened.isEmpty()) {
            say(server, "There was no Blackened among you.");
        } else {
            say(server, "The Blackened was: " + String.join(", ", blackenedNames) + "!");
            if (accused != null && blackened.contains(accused)) {
                say(server, "That's correct! Only the Blackened gets punished.");
            } else {
                say(server, "Wrong! The Blackened walks free, and everyone else gets punished!");
            }
        }
        say(server, "The Master Mind decides what happens next.");
        bell(server, 0.6f);

        clearAllItems(server);
        reset();
    }

    // ------------------------------------------------------------------ cancel / tick

    public static void cancel(MinecraftServer server, String reason) {
        if (phase == Phase.NONE) return;
        clearAllItems(server);
        reset();
        say(server, reason);
    }

    public static void tick(MinecraftServer server) {
        if (phase == Phase.NONE) return;
        long now = server.getTickCount();

        List<Scheduled> due = new ArrayList<>();
        Iterator<Scheduled> it = queue.iterator();
        while (it.hasNext()) {
            Scheduled s = it.next();
            if (s.tick() <= now) {
                due.add(s);
                it.remove();
            }
        }
        for (Scheduled s : due) s.action().run();

        if (now % 20 == 0 && !Game.isActive(server)) {
            cancel(server, "The game ended, so the class trial was cancelled.");
            return;
        }
        if (phase == Phase.DEBATE) {
            tickCountdown(server, now, true);
        } else if (phase == Phase.VOTING) {
            tickCountdown(server, now, false);
        }
    }

    private static void tickCountdown(MinecraftServer server, long now, boolean debate) {
        int remaining = (int) Math.ceil((phaseEnd - now) / 20.0);
        if (remaining <= 0) {
            if (debate) {
                startVoting(server, "The debate time is over.");
            } else {
                finish(server);
            }
            return;
        }
        if (remaining == lastShown) return;
        lastShown = remaining;

        String clock = String.format("%02d:%02d", remaining / 60, remaining % 60);
        String label = debate ? "Debate" : "Voting";
        String extra = debate
                ? "  |  Skip votes: " + skipVotes.size() + "/" + online(server).size()
                : "  |  Votes: " + votes.size() + "/" + online(server).size();
        for (ServerPlayer p : online(server)) {
            p.displayClientMessage(Util.txt(label + " time left: " + clock + extra, ChatFormatting.GOLD), true);
        }
        if ((debate ? DEBATE_ANNOUNCE : VOTE_ANNOUNCE).contains(remaining)) {
            say(server, remaining >= 60
                    ? (remaining / 60) + " minute(s) left!"
                    : remaining + " seconds left!");
        }
    }
}
