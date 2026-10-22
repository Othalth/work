package com.otabletdp;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** The Master Mind's management menu (double chest). Only opens for the owner. */
public final class MasterGui {
    private MasterGui() {}

    public static void open(ServerPlayer player) {
        if (!Util.isMaster(player)) {
            Util.say(player, Util.txt("Only the Master Mind can use this.", ChatFormatting.RED));
            return;
        }
        MinecraftServer server = player.getServer();
        Gui gui = new Gui("master", 6, Component.literal("Master Mind Controls"));
        Util.border(gui, 6, Items.RED_STAINED_GLASS_PANE);

        button(gui, 10, Items.LIME_WOOL, "Start Game", ChatFormatting.GREEN,
                "Starts the game and rolls Ultimates for everyone.",
                () -> {
                    Util.fn(server, "dr:game_start");
                    Util.say(player, Util.txt("The game has started.", ChatFormatting.GREEN));
                });
        button(gui, 11, Items.RED_WOOL, "End Game", ChatFormatting.RED,
                "Ends the game, ends any class trial, wipes the inventory and Ultimate of every player, clears bodies.",
                () -> confirm(player, "End the whole game?", () -> {
                    Trial.cancel(server, "The game has ended.");
                    Blackout.fix(server, null);
                    Util.fn(server, "dr:game_end");
                    ModData.clearDoors();
                    Util.say(player, Util.txt("The game has ended.", ChatFormatting.RED));
                }));
        button(gui, 12, Items.ENDER_PEARL, "Reroll Ultimates", ChatFormatting.LIGHT_PURPLE,
                "Gives every player a new random Ultimate.",
                () -> confirm(player, "Reroll everyone's Ultimate?", () -> {
                    Util.fn(server, "dr:reroll_all");
                    Util.say(player, Util.txt("Ultimates rerolled.", ChatFormatting.GREEN));
                }));
        button(gui, 13, Items.LAVA_BUCKET, "Reset Inventories", ChatFormatting.GOLD,
                "Wipes the inventory and Ultimate of everyone who has one. They roll new Ultimates right away.",
                () -> confirm(player, "Wipe all player inventories?", () -> {
                    Util.fn(server, "dr:reset_inventories");
                    Util.say(player, Util.txt("Player inventories were wiped.", ChatFormatting.GREEN));
                }));
        button(gui, 14, Items.IRON_DOOR, "Reset Dorm Doors", ChatFormatting.GRAY,
                "Every dorm door becomes unclaimed again. Keys keep their owners.",
                () -> confirm(player, "Reset all dorm doors?", () -> {
                    ModData.clearDoors();
                    Util.say(player, Util.txt("All dorm doors are unclaimed again.", ChatFormatting.GREEN));
                }));
        button(gui, 15, Items.SKELETON_SKULL, "Clear Bodies", ChatFormatting.DARK_RED,
                "Removes every body, blood trail and carried-body wool.",
                () -> {
                    Util.fn(server, "dr:clear_bodies");
                    Util.say(player, Util.txt("All bodies were cleared.", ChatFormatting.GREEN));
                });

        button(gui, 19, Items.REDSTONE_TORCH, "Start Blackout", ChatFormatting.YELLOW,
                "Blinds everyone except you.",
                () -> {
                    boolean ok = Blackout.start(server, null);
                    Util.say(player, Util.txt(ok ? "Blackout started." : "A blackout is already active.",
                            ChatFormatting.GREEN));
                });
        button(gui, 20, Items.REDSTONE_LAMP, "Force-end Blackout", ChatFormatting.YELLOW,
                "Restores everyone's sight without repairing the generator.",
                () -> {
                    Blackout.fix(server, null);
                    Util.say(player, Util.txt("Blackout ended.", ChatFormatting.GREEN));
                });
        button(gui, 22, Items.BELL, "Start Class Trial", ChatFormatting.GOLD,
                "Teleports all living players onto the trial podiums and starts the trial.",
                () -> confirm(player, "Start the class trial?", () -> {
                    String problem = Trial.start(server);
                    Util.say(player, problem == null
                            ? Util.txt("The class trial is starting.", ChatFormatting.GREEN)
                            : Util.txt(problem, ChatFormatting.RED));
                }));
        button(gui, 23, Items.BARRIER, "Cancel Class Trial", ChatFormatting.RED,
                "Stops the class trial and removes the wool.",
                () -> {
                    Trial.cancel(server, "The class trial was cancelled.");
                    Util.say(player, Util.txt("Class trial cancelled.", ChatFormatting.GREEN));
                });

        gui.set(49, Util.named(Items.RED_WOOL, Util.txt("Back", ChatFormatting.RED), null),
                () -> TabletGui.openMain(player));
        gui.open(player);
    }

    private static void button(Gui gui, int slot, Item icon, String name, ChatFormatting color, String hint,
                               Runnable action) {
        gui.set(slot, Util.named(icon, Util.txt(name, color), List.of(Util.txt(hint, ChatFormatting.GRAY))), action);
    }

    private static void confirm(ServerPlayer player, String question, Runnable action) {
        Gui gui = new Gui("master_confirm", 3, Component.literal("Are you sure?"));
        Util.border(gui, 3, Items.RED_STAINED_GLASS_PANE);
        gui.set(11, Util.named(Items.LIME_WOOL, Util.txt("Yes, do it", ChatFormatting.GREEN), null), () -> {
            player.closeContainer();
            action.run();
        });
        gui.set(13, Util.named(Items.PAPER, Util.txt(question, ChatFormatting.WHITE), null));
        gui.set(15, Util.named(Items.RED_WOOL, Util.txt("Cancel", ChatFormatting.RED), null), () -> open(player));
        gui.open(player);
    }
}
