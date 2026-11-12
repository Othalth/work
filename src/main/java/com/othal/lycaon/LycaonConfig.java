package com.othal.lycaon;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Saved to <game dir>/config/lycaon.json. Contains the API key in plain text: never share this file. */
public class LycaonConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static LycaonConfig INSTANCE = new LycaonConfig();

    public String apiKey = "";
    // Any OpenAI-compatible endpoint works (Gemini, Groq, OpenRouter, Ollama at http://localhost:11434/v1 ...)
    public String baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai";
    public String model = "gemini-3.5-flash"; // check your provider's current model list and change if needed
    public int thinkSeconds = 90;           // idle time between life decisions
    public int maxCallsPerMinute = 4;       // protects free-tier limits
    public String personality =
        "You are Von Lycaon, a composed, impeccably polite and quietly protective man who carries himself like a "
      + "butler. You speak formally and carefully, with a dry sense of humor, and you are secretly fond of the people "
      + "you look after. You live in a Minecraft world as an independent person with your own goals. "
      + "(Edit this text in config/lycaon.json to match your version of him.)";

    public static LycaonConfig get() { return INSTANCE; }

    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("lycaon.json"); }

    public static void load() {
        try {
            if (Files.exists(file())) {
                LycaonConfig loaded = GSON.fromJson(Files.readString(file()), LycaonConfig.class);
                if (loaded != null) INSTANCE = loaded;
            }
            save(); // writes defaults / newly added fields
        } catch (Exception e) {
            LycaonMod.LOGGER.error("Could not load lycaon.json", e);
        }
    }

    public static void save() {
        try {
            Files.writeString(file(), GSON.toJson(INSTANCE));
        } catch (IOException e) {
            LycaonMod.LOGGER.error("Could not save lycaon.json", e);
        }
    }
}
