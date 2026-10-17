package com.otabletdp;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class ModContent {
    private ModContent() {}

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(OTabletDp.ID, path);
    }

    public static final Item TABLET = Registry.register(BuiltInRegistries.ITEM, id("tablet"),
            new TabletItem(new Item.Properties().stacksTo(1)));

    public static final Item WRENCH = Registry.register(BuiltInRegistries.ITEM, id("wrench"),
            new Item(new Item.Properties().stacksTo(1)));

    public static final Block GENERATOR = Registry.register(BuiltInRegistries.BLOCK, id("generator"),
            new GeneratorBlock(BlockBehaviour.Properties.of().strength(3.0f, 6.0f).sound(SoundType.METAL)));

    public static final Item GENERATOR_ITEM = Registry.register(BuiltInRegistries.ITEM, id("generator"),
            new BlockItem(GENERATOR, new Item.Properties()));

    /** Touching this method forces the static fields above to register. */
    public static void init() {}
}
