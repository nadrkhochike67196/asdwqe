package com.example.companion;

import com.example.companion.entity.CompanionEntity;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class CompanionMod implements ModInitializer {
    public static final String MOD_ID = "companion";

    public static final EntityType<CompanionEntity> COMPANION = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(MOD_ID, "companion"),
            FabricEntityTypeBuilder.create(SpawnGroup.CREATURE, CompanionEntity::new)
                    .dimensions(EntityDimensions.fixed(0.6f, 1.95f))
                    .build()
    );

    @Override
    public void onInitialize() {
        FabricDefaultAttributeRegistry.register(COMPANION, CompanionEntity.createCompanionAttributes());
        Registry.register(Registries.ITEM, new Identifier(MOD_ID, "companion_spawn_egg"),
                new SpawnEggItem(COMPANION, 0x3498DB, 0xF1C40F, new FabricItemSettings()));
    }
}
