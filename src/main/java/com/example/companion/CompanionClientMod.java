package com.example.companion;

import com.example.companion.client.CompanionModel;
import com.example.companion.client.CompanionRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.util.Identifier;

public class CompanionClientMod implements ClientModInitializer {
    public static final EntityModelLayer COMPANION_MODEL_LAYER = new EntityModelLayer(
            new Identifier(CompanionMod.MOD_ID, "companion"), "main");

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(CompanionMod.COMPANION, CompanionRenderer::new);
        EntityModelLayerRegistry.registerModelLayer(COMPANION_MODEL_LAYER, CompanionModel::getTexturedModelData);
    }
}
