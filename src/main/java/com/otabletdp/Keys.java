package com.otabletdp;

import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Blank Keys become the property of the first player who carries them. */
public final class Keys {
    private Keys() {}

    public static UUID ownerOf(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        String id = data.copyTag().getString("dr_key_owner");
        if (id.isEmpty()) return null;
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static String ownerName(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? "" : data.copyTag().getString("dr_key_name");
    }

    public static void bind(ItemStack stack, ServerPlayer player) {
        String id = player.getUUID().toString();
        String name = player.getName().getString();
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putString("dr_key_owner", id);
            tag.putString("dr_key_name", name);
        });
        stack.set(DataComponents.CUSTOM_NAME, Util.txt("Key of " + name, ChatFormatting.GOLD));
    }

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
                ItemStack stack = p.getInventory().getItem(i);
                if (stack.is(ModContent.BLANK_KEY) && ownerOf(stack) == null) {
                    bind(stack, p);
                }
            }
        }
    }
}
