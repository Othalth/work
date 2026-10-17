package com.otabletdp;

import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.Scoreboard;

/** The 16 Ultimates. Index = the value of the "dr.ult" scoreboard score from the datapack (1..16). */
public final class Ultimates {
    private Ultimates() {}

    public static final int LUCKY_STUDENT = 1;

    public record Info(String name, Item pane, ChatFormatting color, String[] desc) {}

    private static final String NONE = "No special abilities yet.";

    private static final Info[] ALL = {
        new Info("Lucky Student", Items.LIME_STAINED_GLASS_PANE, ChatFormatting.GREEN,
                new String[] {"Fortune always seems to find you.", "Gets: Luck, always active.", "Perk: starts generator repairs 25% done."}),
        new Info("Detective", Items.PURPLE_STAINED_GLASS_PANE, ChatFormatting.DARK_PURPLE,
                new String[] {"Nothing escapes your eyes.", "Gets: a Spyglass."}),
        new Info("Affluent Progeny", Items.YELLOW_STAINED_GLASS_PANE, ChatFormatting.GOLD,
                new String[] {"Born to rule, and to discipline.", "Gets: the Discipline Stick (Knockback I)."}),
        new Info("Swimming Pro", Items.LIGHT_BLUE_STAINED_GLASS_PANE, ChatFormatting.AQUA,
                new String[] {"At home in the water.", "Gets: Dolphin's Grace and Conduit Power."}),
        new Info("Writing Prodigy", Items.PINK_STAINED_GLASS_PANE, ChatFormatting.LIGHT_PURPLE,
                new String[] {"Words are your weapon.", "Gets: 3 Book and Quills."}),
        new Info("Gambler", Items.WHITE_STAINED_GLASS_PANE, ChatFormatting.WHITE,
                new String[] {"Always playing the odds.", NONE}),
        new Info("Biker Gang Leader", Items.RED_STAINED_GLASS_PANE, ChatFormatting.RED,
                new String[] {"Loud, loyal and strong.", "Gets: Strength I."}),
        new Info("Baseball Star", Items.ORANGE_STAINED_GLASS_PANE, ChatFormatting.YELLOW,
                new String[] {"A hero of the diamond.", NONE}),
        new Info("Programmer", Items.BLUE_STAINED_GLASS_PANE, ChatFormatting.BLUE,
                new String[] {"Logic is your language.", NONE}),
        new Info("Martial Artist", Items.BROWN_STAINED_GLASS_PANE, ChatFormatting.DARK_RED,
                new String[] {"Strength with a gentle heart.", NONE}),
        new Info("Fanfic Creator", Items.GREEN_STAINED_GLASS_PANE, ChatFormatting.DARK_GREEN,
                new String[] {"Your imagination knows no limits.", NONE}),
        new Info("Moral Compass", Items.LIGHT_GRAY_STAINED_GLASS_PANE, ChatFormatting.GRAY,
                new String[] {"Rules exist for a reason.", NONE}),
        new Info("Clairvoyant", Items.CYAN_STAINED_GLASS_PANE, ChatFormatting.DARK_AQUA,
                new String[] {"Your hunches are... sometimes right.", NONE}),
        new Info("Pop Sensation", Items.MAGENTA_STAINED_GLASS_PANE, ChatFormatting.LIGHT_PURPLE,
                new String[] {"The spotlight loves you.", NONE}),
        new Info("Fashionista", Items.GRAY_STAINED_GLASS_PANE, ChatFormatting.DARK_GRAY,
                new String[] {"Style is survival.", NONE}),
        new Info("Soldier", Items.BLACK_STAINED_GLASS_PANE, ChatFormatting.DARK_GRAY,
                new String[] {"Trained for the worst.", NONE}),
    };

    public static Info info(int index) {
        return ALL[index - 1];
    }

    /** The player's Ultimate number (1..16), or -1 if they have none. */
    public static int of(ServerPlayer player) {
        if (player.getServer() == null) return -1;
        Scoreboard scoreboard = player.getServer().getScoreboard();
        Objective objective = scoreboard.getObjective("dr.ult");
        if (objective == null) return -1;
        ReadOnlyScoreInfo score = scoreboard.getPlayerScoreInfo(player, objective);
        if (score == null) return -1;
        int value = score.value();
        return (value >= 1 && value <= ALL.length) ? value : -1;
    }

    /** In the game and not a spectator. */
    public static boolean isAlive(ServerPlayer player) {
        return !player.isSpectator() && of(player) >= 1;
    }
}
