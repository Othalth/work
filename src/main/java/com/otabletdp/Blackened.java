package com.otabletdp;

import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** The player who committed a kill. Only they are told. */
public final class Blackened {
    private Blackened() {}

    public static final String TAG = "blackened";

    public static void mark(ServerPlayer killer, String victimName) {
        if (killer.getTags().contains("mastermind")) return;
        boolean already = killer.getTags().contains(TAG);
        killer.addTag(TAG);
        String text = already
                ? "You killed " + victimName + ". You are still the Blackened. Keep it secret!"
                : "You killed " + victimName + ". You are now the Blackened. Keep it secret!";
        Util.say(killer, Util.txt(text, ChatFormatting.DARK_RED));
        Util.sound(killer, "minecraft:entity.wither.ambient", 0.8f);
    }

    public static void markByUuid(MinecraftServer server, UUID killerId, String victimName) {
        ServerPlayer killer = server.getPlayerList().getPlayer(killerId);
        if (killer != null) mark(killer, victimName);
    }
}
