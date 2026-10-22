package com.otabletdp;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Leads a player to a place with a trail of particles. */
public final class Guide {
    private Guide() {}

    public record Target(String dim, double x, double y, double z, String label) {}

    private static final Map<UUID, Target> targets = new HashMap<>();

    public static boolean isGuiding(UUID id) {
        return targets.containsKey(id);
    }

    public static void start(ServerPlayer player, Target target) {
        targets.put(player.getUUID(), target);
        Util.say(player, Util.txt("Following: " + target.label() + ". Follow the glowing trail!", ChatFormatting.AQUA));
    }

    public static void stop(ServerPlayer player, boolean message) {
        if (targets.remove(player.getUUID()) != null && message) {
            Util.say(player, Util.txt("Guidance stopped.", ChatFormatting.GRAY));
        }
    }

    public static void reset() {
        targets.clear();
    }

    public static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        if (now % 4 != 0) return;

        Iterator<Map.Entry<UUID, Target>> it = targets.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Target> entry = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(entry.getKey());
            Target t = entry.getValue();
            if (p == null || !p.isAlive()) {
                it.remove();
                continue;
            }
            String dim = p.level().dimension().location().toString();
            if (!dim.equals(t.dim())) {
                Util.say(p, Util.txt("That place is in another dimension. Guidance stopped.", ChatFormatting.GRAY));
                it.remove();
                continue;
            }

            Vec3 pos = p.position();
            double dx = t.x() - pos.x;
            double dy = t.y() - pos.y;
            double dz = t.z() - pos.z;
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

            if (horizontal < 3.5 && Math.abs(dy) < 4.0) {
                Util.say(p, Util.txt("You arrived: " + t.label(), ChatFormatting.GREEN));
                it.remove();
                continue;
            }

            // particles along a straight line towards the target
            double reach = Math.min(dist - 1.0, 14.0);
            for (double d = 2.0; d <= reach; d += 2.0) {
                double px = pos.x + dx / dist * d;
                double py = pos.y + 1.0 + dy / dist * d;
                double pz = pos.z + dz / dist * d;
                p.serverLevel().sendParticles(p, ParticleTypes.END_ROD, true, px, py, pz, 1, 0.0, 0.0, 0.0, 0.0);
            }

            if (now % 20 == 0) {
                p.displayClientMessage(Util.txt(t.label() + "  -  " + Math.round(dist) + " blocks", ChatFormatting.AQUA), true);
            }
        }
    }
}
