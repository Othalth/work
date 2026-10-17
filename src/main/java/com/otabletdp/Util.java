package com.otabletdp;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;

public final class Util {
    private Util() {}

    /** Non-italic coloured text (item names are italic by default). */
    public static Component txt(String text, ChatFormatting format) {
        return Component.literal(text).withStyle(style -> style.withItalic(false).applyFormat(format));
    }

    /** A glass pane / filler with no tooltip. */
    public static ItemStack pane(Item item) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.HIDE_TOOLTIP, Unit.INSTANCE);
        return stack;
    }

    public static ItemStack named(Item item, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, name);
        if (lore != null && !lore.isEmpty()) {
            stack.set(DataComponents.LORE, new ItemLore(lore));
        }
        return stack;
    }

    /** A player head wearing the target player's skin. */
    public static ItemStack head(ServerPlayer target, Component name, List<Component> lore) {
        ItemStack stack = named(Items.PLAYER_HEAD, name, lore);
        stack.set(DataComponents.PROFILE, new ResolvableProfile(target.getGameProfile()));
        return stack;
    }

    /** Fill the outer ring of a chest menu with one kind of pane. */
    public static void border(Gui gui, int rows, Item paneItem) {
        for (int slot = 0; slot < rows * 9; slot++) {
            int row = slot / 9;
            int col = slot % 9;
            if (row == 0 || row == rows - 1 || col == 0 || col == 8) {
                gui.set(slot, pane(paneItem));
            }
        }
    }

    public static void cmd(MinecraftServer server, String command) {
        if (server == null) return;
        server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withSuppressedOutput(), command);
    }

    public static void sound(ServerPlayer player, String sound, float pitch) {
        cmd(player.getServer(), "execute as " + player.getUUID() + " at @s run playsound "
                + sound + " master @s ~ ~ ~ 1 " + pitch);
    }

    public static void say(ServerPlayer player, Component message) {
        player.sendSystemMessage(message);
    }
}
