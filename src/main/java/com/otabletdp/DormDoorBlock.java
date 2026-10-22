package com.otabletdp;

import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A door that belongs to whoever's key claims it first.
 * Only a key registered to the same owner opens it. A lock pick can force it open.
 */
public class DormDoorBlock extends DoorBlock {
    public DormDoorBlock(Properties properties) {
        super(BlockSetType.IRON, properties);
    }

    /** The door is identified by the position of its lower half. */
    public static BlockPos base(BlockState state, BlockPos pos) {
        return state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        boolean isKey = stack.is(ModContent.BLANK_KEY);
        boolean isPick = stack.is(ModContent.LOCK_PICK);
        if (!isKey && !isPick) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide() && player instanceof ServerPlayer sp) {
            BlockPos base = base(state, pos);
            String doorKey = ModData.Loc.of(level, base).key();
            String owner = ModData.doorOwner(doorKey);
            if (isKey) {
                useKey(sp, stack, level, state, pos, doorKey, owner);
            } else {
                usePick(sp, level, base, owner);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    private void useKey(ServerPlayer sp, ItemStack key, Level level, BlockState state, BlockPos pos,
                        String doorKey, String owner) {
        UUID keyOwner = Keys.ownerOf(key);
        if (keyOwner == null) {
            Keys.bind(key, sp);
            keyOwner = sp.getUUID();
        }
        if (owner == null) {
            ModData.claimDoor(doorKey, keyOwner.toString());
            String name = Keys.ownerName(key);
            Util.say(sp, Util.txt("This room now belongs to " + name + ".", ChatFormatting.GREEN));
            Util.sound(sp, "minecraft:block.iron_door.close", 1.0f);
        } else if (owner.equals(keyOwner.toString())) {
            boolean open = state.getValue(DoorBlock.OPEN);
            this.setOpen(sp, level, state, pos, !open);
        } else {
            Util.say(sp, Util.txt("This key doesn't fit this door.", ChatFormatting.RED));
            Util.sound(sp, "minecraft:block.chest.locked", 1.0f);
        }
    }

    private void usePick(ServerPlayer sp, Level level, BlockPos base, String owner) {
        if (owner == null) {
            Util.say(sp, Util.txt("Nobody has claimed this room yet. There is nothing to pick.", ChatFormatting.GRAY));
            return;
        }
        LockPick.start(sp, level, base);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer sp) {
            String owner = ModData.doorOwner(ModData.Loc.of(level, base(state, pos)).key());
            if (owner == null) {
                Util.say(sp, Util.txt("This room isn't claimed yet. Use your key on the door.", ChatFormatting.GRAY));
            } else {
                Util.say(sp, Util.txt("It's locked.", ChatFormatting.RED));
                Util.sound(sp, "minecraft:block.chest.locked", 1.0f);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide() && !state.is(newState.getBlock())) {
            ModData.removeDoor(ModData.Loc.of(level, base(state, pos)).key());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
