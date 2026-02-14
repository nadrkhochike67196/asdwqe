package com.example.companion.entity.ai;

import com.example.companion.entity.CompanionEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

public class WorldPerceptionSystem {

    public static String buildContext(CompanionEntity companion) {
        StringBuilder sb = new StringBuilder();
        World world = companion.getWorld();
        BlockPos pos = companion.getBlockPos();

        // Self
        sb.append("{\"self\":{");
        sb.append("\"hp\":").append((int) companion.getHealth()).append(",");
        sb.append("\"maxHp\":").append((int) companion.getMaxHealth()).append(",");
        sb.append("\"pos\":\"").append(pos.toShortString()).append("\",");
        sb.append("\"dimension\":\"").append(world.getRegistryKey().getValue().toString()).append("\",");
        sb.append("\"inventory\":[");
        SimpleInventory inv = companion.getCompanionInventory();
        boolean first = true;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (!stack.isEmpty()) {
                if (!first) sb.append(",");
                sb.append("\"").append(stack.getCount()).append("x ").append(stack.getName().getString()).append("\"");
                first = false;
            }
        }
        sb.append("]},");

        // Owner
        PlayerEntity owner = companion.getOwnerPlayer();
        if (owner != null) {
            sb.append("\"owner\":{");
            sb.append("\"name\":\"").append(owner.getName().getString()).append("\",");
            sb.append("\"hp\":").append((int) owner.getHealth()).append(",");
            sb.append("\"distance\":").append(String.format("%.0f", companion.distanceTo(owner))).append(",");
            sb.append("\"pos\":\"").append(owner.getBlockPos().toShortString()).append("\"");
            sb.append("},");
        }

        // Time
        long time = world.getTimeOfDay() % 24000;
        sb.append("\"time\":\"").append(time < 13000 ? "Day" : "Night").append("\",");

        // Nearby hostiles
        sb.append("\"hostiles\":[");
        var hostiles = world.getEntitiesByClass(HostileEntity.class, companion.getBoundingBox().expand(20.0), e -> true);
        for (int i = 0; i < Math.min(hostiles.size(), 5); i++) {
            if (i > 0) sb.append(",");
            Entity e = hostiles.get(i);
            sb.append("{\"type\":\"").append(e.getType().getName().getString()).append("\",");
            sb.append("\"dist\":").append(String.format("%.0f", companion.distanceTo(e))).append("}");
        }
        sb.append("],");

        // Nearby items on ground
        sb.append("\"groundItems\":[");
        var items = world.getEntitiesByClass(ItemEntity.class, companion.getBoundingBox().expand(8.0), e -> true);
        for (int i = 0; i < Math.min(items.size(), 5); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(items.get(i).getStack().getName().getString()).append("\"");
        }
        sb.append("],");

        // Nearby notable blocks
        sb.append("\"nearbyBlocks\":[");
        first = true;
        for (int dx = -8; dx <= 8; dx += 2) {
            for (int dy = -4; dy <= 4; dy++) {
                for (int dz = -8; dz <= 8; dz += 2) {
                    BlockPos bp = pos.add(dx, dy, dz);
                    BlockState state = world.getBlockState(bp);
                    if (isInteresting(state)) {
                        if (!first) sb.append(",");
                        sb.append("{\"block\":\"").append(state.getBlock().getName().getString()).append("\",");
                        sb.append("\"pos\":\"").append(bp.toShortString()).append("\"}");
                        first = false;
                    }
                }
            }
        }
        sb.append("],");

        // Memory (remembered resources)
        MemorySystem mem = companion.getMemory();
        sb.append("\"memory\":").append(mem.toJsonSummary()).append(",");

        // Progression
        sb.append("\"progression\":").append(ProgressionTracker.getProgressionJson(companion));

        sb.append("}");
        return sb.toString();
    }

    private static boolean isInteresting(BlockState state) {
        return state.isOf(Blocks.COAL_ORE) || state.isOf(Blocks.DEEPSLATE_COAL_ORE) ||
                state.isOf(Blocks.IRON_ORE) || state.isOf(Blocks.DEEPSLATE_IRON_ORE) ||
                state.isOf(Blocks.GOLD_ORE) || state.isOf(Blocks.DEEPSLATE_GOLD_ORE) ||
                state.isOf(Blocks.DIAMOND_ORE) || state.isOf(Blocks.DEEPSLATE_DIAMOND_ORE) ||
                state.isOf(Blocks.OAK_LOG) || state.isOf(Blocks.BIRCH_LOG) || state.isOf(Blocks.SPRUCE_LOG) ||
                state.isOf(Blocks.CRAFTING_TABLE) || state.isOf(Blocks.FURNACE) ||
                state.isOf(Blocks.CHEST) || state.isOf(Blocks.OBSIDIAN) ||
                state.isOf(Blocks.NETHER_PORTAL) || state.isOf(Blocks.END_PORTAL_FRAME) ||
                state.isOf(Blocks.LAVA) || state.isOf(Blocks.WATER);
    }
}
