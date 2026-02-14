package com.example.stalker.client;

import com.example.stalker.StalkerClientMod;
import com.example.stalker.StalkerMod;
import com.example.stalker.entity.StalkerEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.util.Identifier;

public class StalkerRenderer extends MobEntityRenderer<StalkerEntity, StalkerModel> {
    public StalkerRenderer(EntityRendererFactory.Context context) {
        super(context, new StalkerModel(context.getPart(StalkerClientMod.STALKER_MODEL_LAYER)), 0.5f);
    }

    @Override
    public Identifier getTexture(StalkerEntity entity) {
        return new Identifier(StalkerMod.MOD_ID, "textures/entity/stalker.png");
    }
}
