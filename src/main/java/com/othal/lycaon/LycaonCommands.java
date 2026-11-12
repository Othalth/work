package com.othal.lycaon;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public class LycaonCommands {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {

            // /insertAPIkey <key>
            // NOTE: on a multiplayer server you should restrict this to operators (add .requires(...)).
            dispatcher.register(Commands.literal("insertAPIkey")
                .then(Commands.argument("key", StringArgumentType.string())
                    .executes(ctx -> {
                        String key = StringArgumentType.getString(ctx, "key").trim();
                        LycaonConfig.get().apiKey = key;
                        LycaonConfig.save();
                        ctx.getSource().sendSuccess(() -> Component.literal(
                            "API key saved (" + mask(key) + "). Von Lycaon can think now."), false);
                        return 1;
                    })));

            dispatcher.register(Commands.literal("lycaon")
                .then(Commands.literal("spawn").executes(ctx -> {
                    var src = ctx.getSource();
                    BlockPos pos = BlockPos.containing(src.getPosition());
                    var e = ModEntities.spawn(src.getLevel(), pos);
                    src.sendSuccess(() -> Component.literal(e != null ? "Von Lycaon has arrived." : "Could not spawn him here."), false);
                    return 1;
                }))
                .then(Commands.literal("model").then(Commands.argument("name", StringArgumentType.string())
                    .executes(ctx -> {
                        LycaonConfig.get().model = StringArgumentType.getString(ctx, "name");
                        LycaonConfig.save();
                        ctx.getSource().sendSuccess(() -> Component.literal("Model set."), false);
                        return 1;
                    })))
                .then(Commands.literal("url").then(Commands.argument("baseUrl", StringArgumentType.string())
                    .executes(ctx -> {
                        LycaonConfig.get().baseUrl = StringArgumentType.getString(ctx, "baseUrl");
                        LycaonConfig.save();
                        ctx.getSource().sendSuccess(() -> Component.literal("Provider URL set."), false);
                        return 1;
                    }))));
        });
    }

    private static String mask(String key) {
        return key.length() <= 6 ? "******" : key.substring(0, 3) + "..." + key.substring(key.length() - 3);
    }
}
