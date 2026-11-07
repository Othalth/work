package com.othal.lycaon.client;

import com.othal.lycaon.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class LycaonClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.LYCAON, LycaonRenderer::new);
    }
}
