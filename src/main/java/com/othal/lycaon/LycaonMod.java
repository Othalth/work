package com.othal.lycaon;

import com.othal.lycaon.entity.LycaonEntity;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LycaonMod implements ModInitializer {
    public static final String MOD_ID = "lycaon";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LycaonConfig.load();
        ModEntities.register();
        LycaonCommands.register();

        ServerLifecycleEvents.SERVER_STARTING.register(LycaonMemory::load);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> LycaonMemory.save());

        // Anyone near him who talks in chat is talking to him.
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            String text = message.signedContent();
            for (LycaonEntity l : sender.level().getEntitiesOfClass(LycaonEntity.class, sender.getBoundingBox().inflate(32))) {
                l.hearPlayer(sender, text);
            }
        });

        // First time in this world: he appears a short distance away and comes to find you.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 100 != 0) return;
            if (LycaonMemory.get().spawned || server.getPlayerList().getPlayers().isEmpty()) return;
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            ServerLevel level = (ServerLevel) player.level();
            BlockPos start = player.blockPosition().offset(22, 0, 22);
            BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, start);
            ModEntities.spawn(level, ground);
        });
    }
}
