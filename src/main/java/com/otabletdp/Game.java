package com.otabletdp;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;

/** Reads the datapack's "game is running" flag. */
public final class Game {
    private Game() {}

    public static boolean isActive(MinecraftServer server) {
        Scoreboard scoreboard = server.getScoreboard();
        Objective objective = scoreboard.getObjective("dr.tmp");
        if (objective == null) return false;
        ReadOnlyScoreInfo info = scoreboard.getPlayerScoreInfo(ScoreHolder.forNameOnly("#game"), objective);
        return info != null && info.value() == 1;
    }
}
