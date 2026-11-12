package com.othal.lycaon.ai;

import com.google.gson.*;
import com.othal.lycaon.LycaonConfig;
import com.othal.lycaon.LycaonMod;
import net.minecraft.server.MinecraftServer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

/**
 * Talks to any OpenAI-compatible chat endpoint on a background thread (the game never freezes).
 * Replies are handed back on the server thread. A null reply means "no AI answer available".
 */
public final class LycaonBrain {
    public record Reply(String say, String action, String item) {}

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    private static final Gson GSON = new Gson();
    private static final Deque<JsonObject> HISTORY = new ArrayDeque<>();
    private static final Deque<Long> CALL_TIMES = new ArrayDeque<>();

    private static volatile String lastError = "no error yet";
    private static volatile long blockedUntil = 0; // set after an HTTP 429 so we stop hammering the API
    private static boolean busy = false;
    private static Pending pending = null;

    private record Pending(MinecraftServer server, String context, Consumer<Reply> callback) {}

    private static final String FORMAT_RULES = """

        Reply with ONLY one JSON object, no markdown, no code fences:
        {"say": "what you say out loud in chat (1-2 short sentences, may be empty)", "action": "one action", "item": "optional item id for give_item"}

        Allowed actions:
        none           - do nothing special
        go_home        - walk back to your home
        gather_wood    - chop trees with your axe and keep the logs
        craft          - craft tools from your materials (wooden, then stone tools)
        mine_stone     - mine stone for cobblestone (needs a pickaxe)
        mine_coal      - mine coal ore
        mine_iron      - mine iron ore (needs a stone pickaxe)
        build_home     - build a small wooden house for yourself (needs about 13 logs)
        hunt           - hunt cows, pigs, sheep or chickens for food
        collect_items  - walk over and pick up items lying nearby
        explore        - wander off to look around
        follow_player  - stay close to the player for a while
        hug            - walk to the player and hug them
        give_item      - walk to the player and drop an item from your inventory (set "item", e.g. "oak_log")
        carry_player   - pick the player up and carry them for a short while

        Important facts:
        - Nobody can click on you. The player can only talk to you in chat, so hugs, gifts and carrying only happen when asked.
        - Items the player drops near you are picked up automatically, like a normal player would.
        - You live like a real player: wood tools first, then stone tools, then a home, then food and exploring.
        - You fight back against monsters, avoid creepers, and run away when your health is low.
        Rules: stay in character, never mention being an AI or a program, never use emoji or markdown.
        For LIFE_DECISION events pick the next sensible survival step yourself (not "none").
        For chat events, only use an action if the player asked for it or it clearly fits the moment.
        """;

    public static String lastError() { return lastError; }

    public static boolean ready() {
        return !LycaonConfig.get().apiKey.isBlank();
    }

    public static synchronized void ask(MinecraftServer server, String context, boolean isChat, Consumer<Reply> callback) {
        if (!ready()) {
            lastError = "no API key set (use /insertAPIkey)";
            server.execute(() -> callback.accept(null));
            return;
        }

        long nowMs = System.currentTimeMillis();
        if (nowMs < blockedUntil) {
            lastError = "rate limited, AI paused for " + ((blockedUntil - nowMs) / 1000 + 1) + "s";
            server.execute(() -> callback.accept(null));
            return;
        }

        if (busy) {
            // Keep only the newest chat message waiting; drop life decisions (the entity falls back to simple logic).
            if (isChat) pending = new Pending(server, context, callback);
            else server.execute(() -> callback.accept(null));
            return;
        }

        long now = System.currentTimeMillis();
        while (!CALL_TIMES.isEmpty() && now - CALL_TIMES.peekFirst() > 60_000) CALL_TIMES.pollFirst();
        if (CALL_TIMES.size() >= LycaonConfig.get().maxCallsPerMinute) {
            lastError = "call limit reached (maxCallsPerMinute)";
            server.execute(() -> callback.accept(null));
            return;
        }
        CALL_TIMES.addLast(now);
        busy = true;
        send(server, context, callback, 0);
    }

    private static void send(MinecraftServer server, String context, Consumer<Reply> callback, int attempt) {
        LycaonConfig cfg = LycaonConfig.get();
        try {
            JsonArray messages = new JsonArray();
            messages.add(message("system", cfg.personality + FORMAT_RULES));
            synchronized (HISTORY) { for (JsonObject m : HISTORY) messages.add(m); }
            messages.add(message("user", context));

            JsonObject body = new JsonObject();
            body.addProperty("model", cfg.model);
            body.add("messages", messages);
            body.addProperty("temperature", 0.8);

            LycaonMod.LOGGER.info("[Lycaon] AI call -> {} model={}", cfg.baseUrl, cfg.model);
            HttpRequest request = HttpRequest.newBuilder(URI.create(cfg.baseUrl.replaceAll("/+$", "") + "/chat/completions"))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + cfg.apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                .build();

            HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, error) -> {
                if (error != null && attempt < 1) {
                    LycaonMod.LOGGER.warn("[Lycaon] AI request failed ({}), retrying once", error.toString());
                    send(server, context, callback, attempt + 1);
                    return;
                }
                Reply reply = null;
                try {
                    if (error != null) {
                        lastError = "connection failed: " + error.getClass().getSimpleName();
                        LycaonMod.LOGGER.warn("[Lycaon] AI request failed: {}", error.toString());
                    } else if (response.statusCode() / 100 != 2) {
                        lastError = "HTTP " + response.statusCode();
                        if (response.statusCode() == 429) {
                            blockedUntil = System.currentTimeMillis() + 60_000;
                            lastError = "HTTP 429 rate limit - AI paused for 60s";
                        }
                        LycaonMod.LOGGER.warn("[Lycaon] AI returned HTTP {}: {}", response.statusCode(), truncate(response.body()));
                    } else {
                        String content = JsonParser.parseString(response.body()).getAsJsonObject()
                            .getAsJsonArray("choices").get(0).getAsJsonObject()
                            .getAsJsonObject("message").get("content").getAsString();
                        reply = parse(content);
                        LycaonMod.LOGGER.info("[Lycaon] AI replied: {}", truncate(content));
                        remember("user", context);
                        remember("assistant", content);
                    }
                } catch (Exception e) {
                    lastError = "could not read the reply";
                    LycaonMod.LOGGER.warn("[Lycaon] Could not read AI response: {}", e.toString());
                }
                finish(server, callback, reply);
            });
        } catch (Exception e) {
            lastError = "could not send the request";
            LycaonMod.LOGGER.warn("[Lycaon] Could not send AI request: {}", e.toString());
            finish(server, callback, null);
        }
    }

    private static synchronized void finish(MinecraftServer server, Consumer<Reply> callback, Reply reply) {
        busy = false;
        server.execute(() -> callback.accept(reply));
        if (pending != null) {
            Pending p = pending;
            pending = null;
            ask(p.server(), p.context(), true, p.callback());
        }
    }

    private static Reply parse(String content) {
        try {
            int a = content.indexOf('{'), b = content.lastIndexOf('}');
            if (a >= 0 && b > a) {
                JsonObject o = JsonParser.parseString(content.substring(a, b + 1)).getAsJsonObject();
                return new Reply(str(o, "say"), str(o, "action").isBlank() ? "none" : str(o, "action"), str(o, "item"));
            }
        } catch (Exception ignored) { }
        return new Reply(content.trim(), "none", "");
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    }

    private static JsonObject message(String role, String content) {
        JsonObject m = new JsonObject();
        m.addProperty("role", role);
        m.addProperty("content", content);
        return m;
    }

    private static void remember(String role, String content) {
        synchronized (HISTORY) {
            HISTORY.addLast(message(role, content));
            while (HISTORY.size() > 6) HISTORY.pollFirst();
        }
    }

    private static String truncate(String s) { return s.length() > 900 ? s.substring(0, 900) + "..." : s; }

    private LycaonBrain() {}
}
