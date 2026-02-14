package com.example.companion.client;

import com.example.companion.CompanionClientMod;
import com.example.companion.CompanionMod;
import com.example.companion.entity.CompanionEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.util.Identifier;

public class CompanionRenderer extends MobEntityRenderer<CompanionEntity, CompanionModel> {
    public CompanionRenderer(EntityRendererFactory.Context context) {
        super(context, new CompanionModel(context.getPart(CompanionClientMod.COMPANION_MODEL_LAYER)), 0.5f);
    }

    @Override
    public Identifier getTexture(CompanionEntity entity) {
        return new Identifier(CompanionMod.MOD_ID, "textures/entity/companion.png");
    }
}
