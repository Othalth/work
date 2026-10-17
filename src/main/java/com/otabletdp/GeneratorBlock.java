package com.otabletdp;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class GeneratorBlock extends Block {
    public GeneratorBlock(Properties properties) {
        super(properties);
    }

    /** Right-click with an item in hand. The Wrench starts a blackout. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.is(ModContent.WRENCH)) {
            if (!level.isClientSide() && player instanceof ServerPlayer sp) {
                if (Blackout.isActive()) {
                    Util.say(sp, Util.txt("The blackout is already on. Right-click the generator to repair it.",
                            ChatFormatting.YELLOW));
                } else {
                    Blackout.start(sp.getServer(), sp);
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** Right-click with anything else: try to repair. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer sp) {
            if (Blackout.isActive()) {
                Blackout.tryFix(sp);
            } else {
                Util.say(sp, Util.txt("The generator hums steadily.", ChatFormatting.GRAY));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
