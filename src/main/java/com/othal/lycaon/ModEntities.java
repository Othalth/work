package com.othal.lycaon;

import com.othal.lycaon.entity.LycaonEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;

public class ModEntities {
    public static final EntityType<LycaonEntity> LYCAON = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        ResourceLocation.fromNamespaceAndPath(LycaonMod.MOD_ID, "lycaon"),
        EntityType.Builder.of(LycaonEntity::new, MobCategory.CREATURE).sized(0.6f, 1.8f).passengerAttachments(0.9f).build("lycaon"));

    public static void register() {
        FabricDefaultAttributeRegistry.register(LYCAON, LycaonEntity.createAttributes());
    }

    public static LycaonEntity spawn(ServerLevel level, BlockPos pos) {
        LycaonEntity e = LYCAON.spawn(level, pos, MobSpawnType.COMMAND);
        if (e != null) {
            LycaonMemory.get().spawned = true;
            LycaonMemory.save();
        }
        return e;
    }
}
