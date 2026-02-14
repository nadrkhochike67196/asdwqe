package com.example.companion.entity.ai;

import com.example.companion.entity.CompanionEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

public class ProgressionTracker {

    public static String getCurrentPhase(CompanionEntity c) {
        SimpleInventory inv = c.getCompanionInventory();
        String dim = c.getWorld().getRegistryKey().getValue().toString();

        if (dim.contains("the_end")) return "END_FIGHT";
        if (has(inv, Items.ENDER_EYE)) return "STRONGHOLD";
        if (dim.contains("nether")) return "NETHER";
        if (has(inv, Items.DIAMOND_PICKAXE)) return "NETHER_PREP";
        if (has(inv, Items.IRON_PICKAXE)) return "DIAMOND_HUNTING";
        if (has(inv, Items.STONE_PICKAXE)) return "IRON_HUNTING";
        if (has(inv, Items.WOODEN_PICKAXE)) return "STONE_AGE";
        return "START";
    }

    public static String getProgressionJson(CompanionEntity c) {
        SimpleInventory inv = c.getCompanionInventory();
        String dim = c.getWorld().getRegistryKey().getValue().toString();
        String phase = getCurrentPhase(c);

        StringBuilder sb = new StringBuilder("{");
        sb.append("\"hasWood\":").append(has(inv, Items.OAK_LOG, Items.OAK_PLANKS)).append(",");
        sb.append("\"hasStone\":").append(has(inv, Items.COBBLESTONE)).append(",");
        sb.append("\"hasIron\":").append(has(inv, Items.IRON_INGOT)).append(",");
        sb.append("\"hasDiamond\":").append(has(inv, Items.DIAMOND)).append(",");
        sb.append("\"hasPickaxe\":").append(has(inv, Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE)).append(",");
        sb.append("\"hasSword\":").append(has(inv, Items.WOODEN_SWORD, Items.STONE_SWORD, Items.IRON_SWORD, Items.DIAMOND_SWORD)).append(",");
        sb.append("\"hasArmor\":").append(has(inv, Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE)).append(",");
        sb.append("\"hasBucket\":").append(has(inv, Items.BUCKET, Items.WATER_BUCKET, Items.LAVA_BUCKET)).append(",");
        sb.append("\"hasBed\":").append(has(inv, Items.WHITE_BED, Items.RED_BED, Items.BLUE_BED, Items.GREEN_BED)).append(",");
        sb.append("\"hasBlazeRod\":").append(has(inv, Items.BLAZE_ROD)).append(",");
        sb.append("\"hasEnderEye\":").append(has(inv, Items.ENDER_EYE)).append(",");
        sb.append("\"hasEnderPearl\":").append(has(inv, Items.ENDER_PEARL)).append(",");
        sb.append("\"hasBow\":").append(has(inv, Items.BOW)).append(",");
        sb.append("\"hasShield\":").append(has(inv, Items.SHIELD)).append(",");
        sb.append("\"inNether\":").append(dim.contains("nether")).append(",");
        sb.append("\"inEnd\":").append(dim.contains("the_end")).append(",");

        // Count key resources
        sb.append("\"woodCount\":").append(count(inv, Items.OAK_LOG) + count(inv, Items.OAK_PLANKS) / 4).append(",");
        sb.append("\"ironCount\":").append(count(inv, Items.IRON_INGOT) + count(inv, Items.RAW_IRON)).append(",");
        sb.append("\"diamondCount\":").append(count(inv, Items.DIAMOND)).append(",");
        sb.append("\"coalCount\":").append(count(inv, Items.COAL)).append(",");
        sb.append("\"obsidianCount\":").append(count(inv, Items.OBSIDIAN)).append(",");
        sb.append("\"blazeRodCount\":").append(count(inv, Items.BLAZE_ROD)).append(",");
        sb.append("\"enderPearlCount\":").append(count(inv, Items.ENDER_PEARL)).append(",");
        sb.append("\"enderEyeCount\":").append(count(inv, Items.ENDER_EYE)).append(",");

        sb.append("\"phase\":\"").append(phase).append("\",");

        // Add strategy hint
        String strategy = ExpertKnowledgeBase.getOptimalStrategy(phase);
        // Just first line as hint
        String hint = strategy.lines().filter(l -> !l.isBlank()).findFirst().orElse("Прогрессируй дальше!");
        sb.append("\"nextStep\":\"").append(hint.replace("\"", "'").trim()).append("\"");

        sb.append("}");
        return sb.toString();
    }

    private static boolean has(SimpleInventory inv, Item... items) {
        for (int i = 0; i < inv.size(); i++)
            for (Item item : items)
                if (inv.getStack(i).isOf(item)) return true;
        return false;
    }

    private static int count(SimpleInventory inv, Item item) {
        int c = 0;
        for (int i = 0; i < inv.size(); i++)
            if (inv.getStack(i).isOf(item)) c += inv.getStack(i).getCount();
        return c;
    }
}
