package com.example.stalker;

import com.example.stalker.client.StalkerModel;
import com.example.stalker.client.StalkerRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.util.Identifier;

public class StalkerClientMod implements ClientModInitializer {
    public static final EntityModelLayer STALKER_MODEL_LAYER = new EntityModelLayer(new Identifier(StalkerMod.MOD_ID, "stalker"), "main");

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(StalkerMod.STALKER, StalkerRenderer::new);
        EntityModelLayerRegistry.registerModelLayer(STALKER_MODEL_LAYER, StalkerModel::getTexturedModelData);
    }
}
