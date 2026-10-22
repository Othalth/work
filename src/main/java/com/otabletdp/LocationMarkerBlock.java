package com.otabletdp;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** An unbreakable (outside creative) marker. Its name comes from the anvil-renamed item it was placed from. */
public class LocationMarkerBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(5.0, 0.0, 5.0, 11.0, 14.0, 11.0);

    public LocationMarkerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide()) {
            Component custom = stack.get(DataComponents.CUSTOM_NAME);
            String name = custom != null ? custom.getString() : "Unnamed marker";
            ModData.addMarker(level, pos, name);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide() && !state.is(newState.getBlock())) {
            ModData.removeMarker(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
