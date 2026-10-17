package com.otabletdp;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

/**
 * A tiny server-side chest menu. Nothing can be taken out of it or put into it;
 * clicks only trigger the callbacks registered for each slot.
 */
public final class Gui {
    public final String id;
    private final int rows;
    private final Component title;
    private final Container container;
    private final Map<Integer, Runnable> clicks = new HashMap<>();

    public Gui(String id, int rows, Component title) {
        this.id = id;
        this.rows = rows;
        this.title = title;
        this.container = new SimpleContainer(rows * 9);
    }

    public int size() {
        return rows * 9;
    }

    /** Put a plain (non-clickable) item in a slot. */
    public void set(int slot, ItemStack stack) {
        container.setItem(slot, stack);
        clicks.remove(slot);
    }

    /** Put a clickable item in a slot. */
    public void set(int slot, ItemStack stack, Runnable onClick) {
        container.setItem(slot, stack);
        clicks.put(slot, onClick);
    }

    public void open(ServerPlayer player) {
        final MenuType<?> type = rows == 3 ? MenuType.GENERIC_9x3 : MenuType.GENERIC_9x6;
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, p) -> new Menu(type, containerId, inventory, container, rows), title));
    }

    public final class Menu extends ChestMenu {
        Menu(MenuType<?> type, int containerId, Inventory inventory, Container backing, int rowCount) {
            super(type, containerId, inventory, backing, rowCount);
        }

        public String guiId() {
            return id;
        }

        @Override
        public void clicked(int slotId, int button, ClickType clickType, Player player) {
            // always resync so nothing ever ends up on the cursor
            this.sendAllDataToRemote();
            if (slotId >= 0 && slotId < rows * 9
                    && (clickType == ClickType.PICKUP || clickType == ClickType.QUICK_MOVE)) {
                Runnable r = clicks.get(slotId);
                if (r != null) r.run();
            }
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            return ItemStack.EMPTY;
        }
    }
}
