package com.otabletdp;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

/** Lacing food with poison, and what happens to whoever eats it. */
public final class Poison {
    private Poison() {}

    /** Seconds between eating laced food and dying. */
    private static final int DELAY_SECONDS = 20;

    private static final Set<Item> LACEABLE = Set.of(
            Items.BREAD, Items.CARROT, Items.POTATO, Items.BAKED_POTATO,
            Items.BEETROOT, Items.COOKED_BEEF, Items.COOKIE);

    private record Dying(long end, UUID by) {}

    private static final Map<UUID, Dying> dying = new HashMap<>();

    public static void reset() {
        dying.clear();
    }

    public static boolean isLaced(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().contains("dr_laced");
    }

    /** Bottle in the main hand, plain laceable food in the off hand. */
    public static boolean canLace(ItemStack main, ItemStack off) {
        return main.is(ModContent.POISON_BOTTLE) && LACEABLE.contains(off.getItem()) && !isLaced(off);
    }

    public static void lace(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        if (!canLace(main, off)) return;

        ItemStack laced = new ItemStack(off.getItem(), 1);
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("dr_laced", true);
        tag.putString("dr_laced_by", player.getUUID().toString());
        laced.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

        off.shrink(1);
        main.shrink(1);
        give(player, laced);
        give(player, new ItemStack(Items.GLASS_BOTTLE));

        player.displayClientMessage(Util.txt("You laced the food.", ChatFormatting.DARK_GREEN), true);
        Util.sound(player, "minecraft:entity.witch.drink", 1.0f);
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    public static void tick(MinecraftServer server) {
        long now = server.getTickCount();

        // someone is about to finish eating laced food
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.isUsingItem() && isLaced(p.getUseItem())
                    && p.getUseItemRemainingTicks() <= 1 && !dying.containsKey(p.getUUID())) {
                UUID by = null;
                try {
                    CustomData data = p.getUseItem().get(DataComponents.CUSTOM_DATA);
                    String id = data == null ? "" : data.copyTag().getString("dr_laced_by");
                    if (!id.isEmpty()) by = UUID.fromString(id);
                } catch (IllegalArgumentException ignored) {
                }
                dying.put(p.getUUID(), new Dying(now + DELAY_SECONDS * 20L, by));
                p.addEffect(new MobEffectInstance(MobEffects.POISON, DELAY_SECONDS * 20, 0));
                p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, DELAY_SECONDS * 20, 0));
            }
        }

        Iterator<Map.Entry<UUID, Dying>> it = dying.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Dying> entry = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(entry.getKey());
            if (p == null || !p.isAlive()) {
                it.remove();
                continue;
            }
            if (now >= entry.getValue().end()) {
                it.remove();
                UUID by = entry.getValue().by();
                if (by != null && !by.equals(p.getUUID()) && Game.isActive(server) && Ultimates.of(p) >= 1) {
                    Blackened.markByUuid(server, by, p.getName().getString());
                }
                p.hurt(p.damageSources().magic(), Float.MAX_VALUE);
            }
        }
    }
}
