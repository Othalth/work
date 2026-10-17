package com.otabletdp;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

/** The red wool the datapack hands out while you carry a body. Right-click = drop the body. */
public final class DropWool {
    private DropWool() {}

    public static boolean isDropper(ItemStack stack) {
        if (!stack.is(Items.RED_WOOL)) return false;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().contains("dr_drop");
    }

    public static void drop(Player player, boolean clientSide) {
        if (clientSide || !(player instanceof ServerPlayer sp) || sp.getServer() == null) return;
        sp.getServer().getCommands().performPrefixedCommand(
                sp.createCommandSourceStack().withPermission(2).withSuppressedOutput(),
                "function dr:dropbody");
    }
}
