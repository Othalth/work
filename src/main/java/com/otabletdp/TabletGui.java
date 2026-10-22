package com.otabletdp;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

public final class TabletGui {
    private TabletGui() {}

    private static final int HEAD_SLOT = 11;   // middle row, 3rd column
    private static final int MAP_SLOT = 13;    // middle row, centre
    private static final int LIST_SLOT = 15;   // middle row, 7th column
    private static final int BACK_SLOT = 49;   // bottom row, centre

    /** First tab: your own profile, the map, the player list (and, for the Master Mind, the controls). */
    public static void openMain(ServerPlayer player) {
        boolean master = Util.isMaster(player);
        int index = Ultimates.of(player);
        if (!master && index < 1) {
            Util.say(player, Util.txt("You haven't received an Ultimate yet.", ChatFormatting.RED));
            return;
        }

        Gui gui = new Gui("tablet", 3, Component.literal(master ? "Master Mind Tablet" : "O-Tablet"));
        Util.border(gui, 3, Util.paneFor(player));

        List<Component> lore = new ArrayList<>();
        lore.add(Util.txt("Name: " + player.getName().getString(), ChatFormatting.WHITE));
        if (master) {
            lore.add(Util.txt("Master Mind", ChatFormatting.RED));
        } else {
            Ultimates.Info info = Ultimates.info(index);
            lore.add(Util.txt("Ultimate " + info.name(), info.color()));
            lore.add(Component.empty());
            for (String line : info.desc()) {
                lore.add(Util.txt(line, ChatFormatting.GRAY));
            }
        }

        int headSlot = master ? 10 : HEAD_SLOT;
        int mapSlot = master ? 12 : MAP_SLOT;
        int listSlot = master ? 14 : LIST_SLOT;

        gui.set(headSlot, Util.head(player, Util.txt(player.getName().getString(), ChatFormatting.GOLD), lore));

        List<Component> mapLore = List.of(Util.txt("Find places, bodies and generators.", ChatFormatting.GRAY));
        gui.set(mapSlot, Util.named(Items.MAP, Util.txt("Map", ChatFormatting.AQUA), mapLore),
                () -> MapGui.open(player, 0));

        List<Component> listLore = List.of(Util.txt("Click to see who is still alive.", ChatFormatting.GRAY));
        gui.set(listSlot,
                Util.named(Items.CHEST, Util.txt("List of Current Players", ChatFormatting.YELLOW), listLore),
                () -> openPlayers(player));

        if (master) {
            List<Component> adminLore = List.of(Util.txt("Manage the game.", ChatFormatting.GRAY));
            gui.set(16, Util.named(Items.REDSTONE_BLOCK, Util.txt("Controls", ChatFormatting.RED), adminLore),
                    () -> MasterGui.open(player));
        }

        gui.open(player);
    }

    /** Called when the owner uses a Master Mind Tablet: they stop being a player. */
    public static void activateMaster(ServerPlayer player) {
        if (!player.hasPermissions(2)) {
            Util.say(player, Util.txt("Only the Master Mind (an operator) can use this tablet's controls.",
                    ChatFormatting.RED));
            return;
        }
        if (!player.getTags().contains("mastermind")) {
            player.addTag("mastermind");
            Util.fnAs(player, "dr:free");
            Util.say(player, Util.txt("You are now the Master Mind. You are not counted as a player.",
                    ChatFormatting.RED));
        }
    }

    /** Second tab: heads of everyone still alive. */
    public static void openPlayers(ServerPlayer player) {
        Gui gui = new Gui("tablet_players", 6, Component.literal("Current Players"));
        Util.border(gui, 6, Util.paneFor(player));

        int n = 0;
        for (ServerPlayer other : player.getServer().getPlayerList().getPlayers()) {
            if (!Ultimates.isAlive(other)) continue;
            if (n >= 28) break; // 4 rows x 7 columns inside the border
            int slot = (1 + n / 7) * 9 + 1 + n % 7;
            gui.set(slot, Util.head(other, Util.txt(other.getName().getString(), ChatFormatting.WHITE), null));
            n++;
        }

        gui.set(BACK_SLOT,
                Util.named(Items.RED_WOOL, Util.txt("Back", ChatFormatting.RED), null),
                () -> openMain(player));

        gui.open(player);
    }
}
