package com.otabletdp;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;
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

    // ---- map markers ----
    public static final Block LOCATION_MARKER = Registry.register(BuiltInRegistries.BLOCK, id("location_marker"),
            new LocationMarkerBlock(BlockBehaviour.Properties.of()
                    .strength(-1.0f, 3600000.0f).noLootTable().noOcclusion().lightLevel(state -> 10)
                    .sound(SoundType.AMETHYST)));

    public static final Item LOCATION_MARKER_ITEM = Registry.register(BuiltInRegistries.ITEM, id("location_marker"),
            new BlockItem(LOCATION_MARKER, new Item.Properties()));

    // ---- tools and weapons ----
    public static final Item SCREWDRIVER = Registry.register(BuiltInRegistries.ITEM, id("screwdriver"),
            new ScrewdriverItem(new Item.Properties().durability(10)));

    public static final Item POISON_BOTTLE = Registry.register(BuiltInRegistries.ITEM, id("poison_bottle"),
            new Item(new Item.Properties().stacksTo(16)));

    public static final Item WOODEN_HAMMER = Registry.register(BuiltInRegistries.ITEM, id("wooden_hammer"),
            new Item(new Item.Properties().stacksTo(1).attributes(hammerAttributes())));

    private static ItemAttributeModifiers hammerAttributes() {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(id("hammer_damage"), 8.0, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED,
                        new AttributeModifier(id("hammer_speed"), -3.1, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
    }

    // ---- dorm doors and keys ----
    public static final Item BLANK_KEY = Registry.register(BuiltInRegistries.ITEM, id("blank_key"),
            new Item(new Item.Properties().stacksTo(1)));

    public static final Item LOCK_PICK = Registry.register(BuiltInRegistries.ITEM, id("lock_pick"),
            new Item(new Item.Properties().durability(5)));

    public static final Block DORM_DOOR = Registry.register(BuiltInRegistries.BLOCK, id("dorm_door"),
            new DormDoorBlock(BlockBehaviour.Properties.of()
                    .strength(-1.0f, 3600000.0f).noLootTable().noOcclusion().sound(SoundType.METAL)));

    public static final Item DORM_DOOR_ITEM = Registry.register(BuiltInRegistries.ITEM, id("dorm_door"),
            new DoubleHighBlockItem(DORM_DOOR, new Item.Properties()));

    // ---- master mind and class trial ----
    public static final Item MASTERMIND_TABLET = Registry.register(BuiltInRegistries.ITEM, id("mastermind_tablet"),
            new TabletItem(new Item.Properties().stacksTo(1)));

    public static final Block TRIAL_TELEPORTER = Registry.register(BuiltInRegistries.BLOCK, id("trial_teleporter"),
            new TrialTeleporterBlock(BlockBehaviour.Properties.of()
                    .strength(-1.0f, 3600000.0f).noLootTable().noOcclusion().sound(SoundType.METAL)));

    public static final Item TRIAL_TELEPORTER_ITEM = Registry.register(BuiltInRegistries.ITEM, id("trial_teleporter"),
            new BlockItem(TRIAL_TELEPORTER, new Item.Properties()));

    /** Touching this method forces the static fields above to register. */
    public static void init() {}
}
