package com.example.companion.entity.ai;

import com.example.companion.entity.CompanionEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

public class ProgressionTracker {
    public static String getProgressionJson(CompanionEntity c) {
        SimpleInventory inv = c.getCompanionInventory();
        String dim = c.getWorld().getRegistryKey().getValue().toString();
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"hasWood\":").append(has(inv, Items.OAK_LOG, Items.OAK_PLANKS)).append(",");
        sb.append("\"hasStone\":").append(has(inv, Items.COBBLESTONE)).append(",");
        sb.append("\"hasIron\":").append(has(inv, Items.IRON_INGOT)).append(",");
        sb.append("\"hasDiamond\":").append(has(inv, Items.DIAMOND)).append(",");
        sb.append("\"hasPickaxe\":").append(has(inv, Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE)).append(",");
        sb.append("\"hasBlazeRod\":").append(has(inv, Items.BLAZE_ROD)).append(",");
        sb.append("\"hasEnderEye\":").append(has(inv, Items.ENDER_EYE)).append(",");
        sb.append("\"inNether\":").append(dim.contains("nether")).append(",");
        sb.append("\"inEnd\":").append(dim.contains("the_end")).append(",");
        String phase = "START";
        if (has(inv, Items.WOODEN_PICKAXE)) phase = "STONE_AGE";
        if (has(inv, Items.STONE_PICKAXE)) phase = "IRON_HUNTING";
        if (has(inv, Items.IRON_PICKAXE)) phase = "DIAMOND_HUNTING";
        if (has(inv, Items.DIAMOND_PICKAXE)) phase = "NETHER_PREP";
        if (dim.contains("nether")) phase = "NETHER";
        if (has(inv, Items.ENDER_EYE)) phase = "STRONGHOLD";
        if (dim.contains("the_end")) phase = "END_FIGHT";
        sb.append("\"phase\":\"").append(phase).append("\"}");
        return sb.toString();
    }

    private static boolean has(SimpleInventory inv, Item... items) {
        for (int i = 0; i < inv.size(); i++)
            for (Item item : items)
                if (inv.getStack(i).isOf(item)) return true;
        return false;
    }
}
