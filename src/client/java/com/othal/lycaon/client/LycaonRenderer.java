package com.othal.lycaon.client;

import com.othal.lycaon.LycaonMod;
import com.othal.lycaon.entity.LycaonEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Renders him with the full player body using a 64x64 skin texture. */
public class LycaonRenderer extends HumanoidMobRenderer<LycaonEntity, HumanoidModel<LycaonEntity>> {
    private static final ResourceLocation TEXTURE =
        ResourceLocation.fromNamespaceAndPath(LycaonMod.MOD_ID, "textures/entity/lycaon.png");

    public LycaonRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(LycaonEntity entity) {
        return TEXTURE;
    }
}
