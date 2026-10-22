package com.otabletdp;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Keeps the trapdoors that were opened with the screwdriver open (even if redstone tries to close them). */
public final class Trapdoors {
    private Trapdoors() {}

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 10 != 0) return;
        for (ModData.Loc loc : ModData.trapdoors()) {
            ServerLevel level = Util.level(server, loc.dim());
            if (level == null) continue;
            BlockPos pos = loc.pos();
            if (!level.hasChunkAt(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (!state.is(Blocks.IRON_TRAPDOOR)) {
                ModData.removeTrapdoorKey(loc.key());
            } else if (!state.getValue(TrapDoorBlock.OPEN)) {
                level.setBlock(pos, state.setValue(TrapDoorBlock.OPEN, true), 3);
            }
        }
    }
}
