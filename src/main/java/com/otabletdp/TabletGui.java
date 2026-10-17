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
    private static final int LIST_SLOT = 15;   // middle row, 7th column
    private static final int BACK_SLOT = 49;   // bottom row, centre

    /** First tab: your own profile + the player list button. */
    public static void openMain(ServerPlayer player) {
        int index = Ultimates.of(player);
        if (index < 1) {
            Util.say(player, Util.txt("You haven't received an Ultimate yet.", ChatFormatting.RED));
            return;
        }
        Ultimates.Info info = Ultimates.info(index);

        Gui gui = new Gui("tablet", 3, Component.literal("O-Tablet"));
        Util.border(gui, 3, info.pane());

        List<Component> lore = new ArrayList<>();
        lore.add(Util.txt("Name: " + player.getName().getString(), ChatFormatting.WHITE));
        lore.add(Util.txt("Ultimate " + info.name(), info.color()));
        lore.add(Component.empty());
        for (String line : info.desc()) {
            lore.add(Util.txt(line, ChatFormatting.GRAY));
        }
        gui.set(HEAD_SLOT, Util.head(player, Util.txt(player.getName().getString(), ChatFormatting.GOLD), lore));

        List<Component> listLore = List.of(Util.txt("Click to see who is still alive.", ChatFormatting.GRAY));
        gui.set(LIST_SLOT,
                Util.named(Items.CHEST, Util.txt("List of Current Players", ChatFormatting.YELLOW), listLore),
                () -> openPlayers(player));

        gui.open(player);
    }

    /** Second tab: heads of everyone still alive. */
    public static void openPlayers(ServerPlayer player) {
        int index = Ultimates.of(player);
        Ultimates.Info info = index >= 1 ? Ultimates.info(index) : null;

        Gui gui = new Gui("tablet_players", 6, Component.literal("Current Players"));
        Util.border(gui, 6, info != null ? info.pane() : Items.GRAY_STAINED_GLASS_PANE);

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
