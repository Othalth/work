package com.otabletdp;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Forces an iron trapdoor open and keeps it open. Breaks after 10 uses. */
public class ScrewdriverItem extends Item {
    public ScrewdriverItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (!state.is(Blocks.IRON_TRAPDOOR)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide() && context.getPlayer() instanceof ServerPlayer player) {
            boolean alreadyDone = state.getValue(TrapDoorBlock.OPEN) && ModData.hasTrapdoor(level, pos);
            if (alreadyDone) {
                Util.say(player, Util.txt("This trapdoor is already forced open.", ChatFormatting.GRAY));
            } else {
                if (!state.getValue(TrapDoorBlock.OPEN)) {
                    level.setBlock(pos, state.setValue(TrapDoorBlock.OPEN, true), 3);
                }
                ModData.addTrapdoor(level, pos);
                Util.cmd(player.getServer(), "playsound minecraft:block.iron_trapdoor.open block @a "
                        + pos.getX() + " " + pos.getY() + " " + pos.getZ());
                ItemStack stack = context.getItemInHand();
                EquipmentSlot slot = context.getHand() == InteractionHand.MAIN_HAND
                        ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
                stack.hurtAndBreak(1, player, slot);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
