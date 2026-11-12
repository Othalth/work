package com.othal.lycaon.client;

import com.othal.lycaon.LycaonMod;
import com.othal.lycaon.entity.LycaonEntity;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

public class LycaonRenderer extends HumanoidMobRenderer<LycaonEntity, LycaonModel> {
    private static final ResourceLocation TEXTURE =
        ResourceLocation.fromNamespaceAndPath(LycaonMod.MOD_ID, "textures/entity/lycaon.png");

    public LycaonRenderer(EntityRendererProvider.Context context) {
        super(context, new LycaonModel(context.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(LycaonEntity entity) {
        return TEXTURE;
    }
}
