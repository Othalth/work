package com.otabletdp;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

/** Everything the mod remembers between sessions, saved as one JSON file in the world folder. */
public final class ModData {
    private ModData() {}

    /** A block position in a named dimension. */
    public record Loc(String dim, int x, int y, int z) {
        public static Loc of(Level level, BlockPos pos) {
            return new Loc(level.dimension().location().toString(), pos.getX(), pos.getY(), pos.getZ());
        }

        public static Loc parse(String s) {
            String[] a = s.split("\\|");
            return new Loc(a[0], Integer.parseInt(a[1]), Integer.parseInt(a[2]), Integer.parseInt(a[3]));
        }

        public String key() {
            return dim + "|" + x + "|" + y + "|" + z;
        }

        public BlockPos pos() {
            return new BlockPos(x, y, z);
        }
    }

    /** A named location marker block. */
    public static final class Marker {
        public String dim;
        public int x;
        public int y;
        public int z;
        public String name;
    }

    private static final class Store {
        List<Marker> markers = new ArrayList<>();
        List<String> generators = new ArrayList<>();
        List<String> trapdoors = new ArrayList<>();
        Map<String, String> doors = new HashMap<>();
        List<String> teleporters = new ArrayList<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Store store = new Store();
    private static Path file = null;

    // ------------------------------------------------------------------ load / save

    public static void load(MinecraftServer server) {
        file = server.getWorldPath(LevelResource.ROOT).resolve("o_tablet_dp_data.json");
        store = new Store();
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                Store loaded = GSON.fromJson(reader, Store.class);
                if (loaded != null) {
                    store = loaded;
                    if (store.markers == null) store.markers = new ArrayList<>();
                    if (store.generators == null) store.generators = new ArrayList<>();
                    if (store.trapdoors == null) store.trapdoors = new ArrayList<>();
                    if (store.doors == null) store.doors = new HashMap<>();
                    if (store.teleporters == null) store.teleporters = new ArrayList<>();
                }
            } catch (Exception e) {
                OTabletDp.LOGGER.error("Could not read o_tablet_dp_data.json", e);
            }
        }
    }

    private static void save() {
        if (file == null) return;
        try (Writer writer = Files.newBufferedWriter(file)) {
            GSON.toJson(store, writer);
        } catch (Exception e) {
            OTabletDp.LOGGER.error("Could not write o_tablet_dp_data.json", e);
        }
    }

    // ------------------------------------------------------------------ markers

    public static List<Marker> markers() {
        return new ArrayList<>(store.markers);
    }

    public static void addMarker(Level level, BlockPos pos, String name) {
        removeMarkerQuiet(level, pos);
        Loc loc = Loc.of(level, pos);
        Marker m = new Marker();
        m.dim = loc.dim();
        m.x = loc.x();
        m.y = loc.y();
        m.z = loc.z();
        m.name = name;
        store.markers.add(m);
        save();
    }

    public static boolean renameMarker(Level level, BlockPos pos, String name) {
        Loc loc = Loc.of(level, pos);
        for (Marker m : store.markers) {
            if (m.dim.equals(loc.dim()) && m.x == loc.x() && m.y == loc.y() && m.z == loc.z()) {
                m.name = name;
                save();
                return true;
            }
        }
        addMarker(level, pos, name);
        return true;
    }

    public static void removeMarker(Level level, BlockPos pos) {
        if (removeMarkerQuiet(level, pos)) save();
    }

    private static boolean removeMarkerQuiet(Level level, BlockPos pos) {
        Loc loc = Loc.of(level, pos);
        return store.markers.removeIf(m -> m.dim.equals(loc.dim()) && m.x == loc.x() && m.y == loc.y() && m.z == loc.z());
    }

    // ------------------------------------------------------------------ generators

    public static List<Loc> generators() {
        List<Loc> out = new ArrayList<>();
        for (String s : store.generators) {
            try {
                out.add(Loc.parse(s));
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    public static void addGenerator(Level level, BlockPos pos) {
        String key = Loc.of(level, pos).key();
        if (!store.generators.contains(key)) {
            store.generators.add(key);
            save();
        }
    }

    public static void removeGenerator(Level level, BlockPos pos) {
        if (store.generators.remove(Loc.of(level, pos).key())) save();
    }

    // ------------------------------------------------------------------ forced-open trapdoors

    public static List<Loc> trapdoors() {
        List<Loc> out = new ArrayList<>();
        for (String s : store.trapdoors) {
            try {
                out.add(Loc.parse(s));
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    public static boolean hasTrapdoor(Level level, BlockPos pos) {
        return store.trapdoors.contains(Loc.of(level, pos).key());
    }

    public static void addTrapdoor(Level level, BlockPos pos) {
        String key = Loc.of(level, pos).key();
        if (!store.trapdoors.contains(key)) {
            store.trapdoors.add(key);
            save();
        }
    }

    public static void removeTrapdoorKey(String key) {
        if (store.trapdoors.remove(key)) save();
    }

    // ------------------------------------------------------------------ dorm doors (key = lower half position)

    public static String doorOwner(String doorKey) {
        return store.doors.get(doorKey);
    }

    public static void claimDoor(String doorKey, String ownerUuid) {
        store.doors.put(doorKey, ownerUuid);
        save();
    }

    public static void removeDoor(String doorKey) {
        if (store.doors.remove(doorKey) != null) save();
    }

    public static void clearDoors() {
        if (!store.doors.isEmpty()) {
            store.doors.clear();
            save();
        }
    }

    // ------------------------------------------------------------------ class trial teleporters

    public static List<Loc> teleporters() {
        List<Loc> out = new ArrayList<>();
        for (String s : store.teleporters) {
            try {
                out.add(Loc.parse(s));
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    public static void addTeleporter(Level level, BlockPos pos) {
        String key = Loc.of(level, pos).key();
        if (!store.teleporters.contains(key)) {
            store.teleporters.add(key);
            save();
        }
    }

    public static void removeTeleporter(Level level, BlockPos pos) {
        if (store.teleporters.remove(Loc.of(level, pos).key())) save();
    }
}
