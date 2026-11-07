package com.othal.lycaon.client;

import com.othal.lycaon.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.entity.EntityRenderers;

public class LycaonClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRenderers.register(ModEntities.LYCAON, LycaonRenderer::new);
    }
}
