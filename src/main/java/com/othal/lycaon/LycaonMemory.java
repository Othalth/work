package com.othal.lycaon;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Per-world memory, saved inside the world folder as lycaon_memory.json. */
public class LycaonMemory {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static LycaonMemory INSTANCE = new LycaonMemory();
    private static Path path;

    public boolean spawned;
    public boolean met;
    public boolean hasHome;
    public boolean homeBuilt;
    public int homeX, homeY, homeZ;
    public int affection;
    public List<String> notes = new ArrayList<>();

    public static LycaonMemory get() { return INSTANCE; }

    public static void load(MinecraftServer server) {
        path = server.getWorldPath(LevelResource.ROOT).resolve("lycaon_memory.json");
        INSTANCE = new LycaonMemory();
        try {
            if (Files.exists(path)) {
                LycaonMemory m = GSON.fromJson(Files.readString(path), LycaonMemory.class);
                if (m != null) INSTANCE = m;
            }
        } catch (Exception e) {
            LycaonMod.LOGGER.error("Could not load Lycaon's memory", e);
        }
    }

    public static void save() {
        if (path == null) return;
        try {
            Files.writeString(path, GSON.toJson(INSTANCE));
        } catch (Exception e) {
            LycaonMod.LOGGER.error("Could not save Lycaon's memory", e);
        }
    }

    public void setHome(BlockPos pos) {
        hasHome = true; homeX = pos.getX(); homeY = pos.getY(); homeZ = pos.getZ();
        save();
    }

    public BlockPos home() { return new BlockPos(homeX, homeY, homeZ); }

    public void note(String text) {
        notes.add(text);
        while (notes.size() > 25) notes.remove(0);
        save();
    }
}
