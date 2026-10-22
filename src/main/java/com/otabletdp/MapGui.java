package com.otabletdp;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** The tablet's "Map" tab: alerts (blackout, bodies) first, then every location marker. */
public final class MapGui {
    private MapGui() {}

    private static final int PER_PAGE = 28; // 4 rows x 7 columns inside the border
    private static final int PREV_SLOT = 48;
    private static final int BACK_SLOT = 49;
    private static final int NEXT_SLOT = 50;
    private static final int STOP_SLOT = 52;

    private record Entry(ItemStack icon, Guide.Target target) {}

    public static void open(ServerPlayer player, int requestedPage) {
        String dim = player.level().dimension().location().toString();
        List<Entry> entries = buildEntries(player, dim);

        int pages = Math.max(1, (entries.size() + PER_PAGE - 1) / PER_PAGE);
        final int page = Math.max(0, Math.min(requestedPage, pages - 1));

        Item pane = Util.paneFor(player);

        Gui gui = new Gui("tablet_map", 6, Component.literal("Map"));
        Util.border(gui, 6, pane);

        if (entries.isEmpty()) {
            gui.set(22, Util.named(Items.PAPER, Util.txt("No locations yet.", ChatFormatting.GRAY), null));
        }

        int start = page * PER_PAGE;
        for (int i = 0; i < PER_PAGE && start + i < entries.size(); i++) {
            Entry entry = entries.get(start + i);
            int slot = (1 + i / 7) * 9 + 1 + i % 7;
            if (entry.target() == null) {
                gui.set(slot, entry.icon());
            } else {
                gui.set(slot, entry.icon(), () -> {
                    player.closeContainer();
                    Guide.start(player, entry.target());
                });
            }
        }

        if (page > 0) {
            gui.set(PREV_SLOT, Util.named(Items.ARROW, Util.txt("Previous page", ChatFormatting.YELLOW), null),
                    () -> open(player, page - 1));
        }
        if (page < pages - 1) {
            gui.set(NEXT_SLOT, Util.named(Items.ARROW, Util.txt("Next page", ChatFormatting.YELLOW), null),
                    () -> open(player, page + 1));
        }
        gui.set(BACK_SLOT, Util.named(Items.RED_WOOL, Util.txt("Back", ChatFormatting.RED), null),
                () -> TabletGui.openMain(player));
        if (Guide.isGuiding(player.getUUID())) {
            gui.set(STOP_SLOT, Util.named(Items.BARRIER, Util.txt("Stop guidance", ChatFormatting.RED), null), () -> {
                Guide.stop(player, true);
                open(player, page);
            });
        }

        gui.open(player);
    }

    private static List<Entry> buildEntries(ServerPlayer player, String dim) {
        MinecraftServer server = player.getServer();
        List<Entry> entries = new ArrayList<>();
        Vec3 here = player.position();

        // 1) blackout alert -> nearest generator
        if (Blackout.isActive()) {
            ModData.Loc nearest = null;
            double best = Double.MAX_VALUE;
            for (ModData.Loc g : ModData.generators()) {
                if (!g.dim().equals(dim)) continue;
                double d = here.distanceToSqr(g.x() + 0.5, g.y() + 0.5, g.z() + 0.5);
                if (d < best) {
                    best = d;
                    nearest = g;
                }
            }
            if (nearest != null) {
                List<Component> lore = new ArrayList<>();
                lore.add(Util.txt("The lights are out! Repair it.", ChatFormatting.GRAY));
                lore.add(Util.txt(Math.round(Math.sqrt(best)) + " blocks away. Click to be guided.", ChatFormatting.GRAY));
                Item generatorItem = ModContent.GENERATOR_ITEM;
                entries.add(new Entry(
                        Util.named(generatorItem, Util.txt("BLACKOUT: nearest generator", ChatFormatting.YELLOW), lore),
                        new Guide.Target(dim, nearest.x() + 0.5, nearest.y() + 0.5, nearest.z() + 0.5, "Generator")));
            } else {
                entries.add(new Entry(
                        Util.named(ModContent.GENERATOR_ITEM,
                                Util.txt("BLACKOUT: no generator registered", ChatFormatting.YELLOW), null),
                        null));
            }
        }

        // 2) discovered bodies (written by the datapack when the discovery alarm goes off)
        CompoundTag alert = server.getCommandStorage().get(ResourceLocation.fromNamespaceAndPath("dr", "alert"));
        ListTag bodies = alert.getList("bodies", Tag.TAG_LIST);
        for (int i = 0; i < bodies.size(); i++) {
            ListTag pos = bodies.getList(i);
            if (pos.size() < 3) continue;
            double x = pos.getDouble(0);
            double y = pos.getDouble(1);
            double z = pos.getDouble(2);
            List<Component> lore = new ArrayList<>();
            lore.add(Util.txt("Where it was discovered.", ChatFormatting.GRAY));
            lore.add(Util.txt(Math.round(here.distanceTo(new Vec3(x, y, z))) + " blocks away. Click to be guided.",
                    ChatFormatting.GRAY));
            entries.add(new Entry(
                    Util.named(Items.SKELETON_SKULL, Util.txt("Body #" + (i + 1), ChatFormatting.RED), lore),
                    new Guide.Target(dim, x, y, z, "Body #" + (i + 1))));
        }

        // 3) location markers, A to Z
        List<ModData.Marker> markers = new ArrayList<>();
        for (ModData.Marker m : ModData.markers()) {
            if (m.dim.equals(dim)) markers.add(m);
        }
        markers.sort(Comparator.comparing(m -> m.name == null ? "" : m.name.toLowerCase()));
        for (ModData.Marker m : markers) {
            String name = (m.name == null || m.name.isBlank()) ? "Unnamed marker" : m.name;
            List<Component> lore = new ArrayList<>();
            lore.add(Util.txt(Math.round(here.distanceTo(new Vec3(m.x + 0.5, m.y + 0.5, m.z + 0.5)))
                    + " blocks away. Click to be guided.", ChatFormatting.GRAY));
            entries.add(new Entry(
                    Util.named(Items.COMPASS, Util.txt(name, ChatFormatting.AQUA), lore),
                    new Guide.Target(dim, m.x + 0.5, m.y + 0.5, m.z + 0.5, name)));
        }
        return entries;
    }
}
