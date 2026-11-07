package com.othal.lycaon;

import com.othal.lycaon.entity.LycaonEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public class ModEntities {
    public static final ResourceKey<EntityType<?>> LYCAON_KEY =
        ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(LycaonMod.MOD_ID, "lycaon"));

    public static final EntityType<LycaonEntity> LYCAON = Registry.register(
        BuiltInRegistries.ENTITY_TYPE, LYCAON_KEY,
        EntityType.Builder.of(LycaonEntity::new, MobCategory.CREATURE).sized(0.6f, 1.8f).build(LYCAON_KEY));

    public static void register() {
        FabricDefaultAttributeRegistry.register(LYCAON, LycaonEntity.createAttributes());
    }

    public static LycaonEntity spawn(ServerLevel level, BlockPos pos) {
        LycaonEntity e = LYCAON.spawn(level, pos, EntitySpawnReason.COMMAND);
        if (e != null) {
            LycaonMemory.get().spawned = true;
            LycaonMemory.save();
        }
        return e;
    }
}
