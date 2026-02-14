package com.example.companion.entity.ai;

import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CraftingChainResolver {

    public record CraftRecipe(String name, Item result, int outputCount, Item[] mats, int[] amounts) {}

    public record CraftStep(String recipeName, Item result, int outputCount, Item[] mats, int[] amounts) {}

    private static final Map<String, CraftRecipe> RECIPES = new LinkedHashMap<>();

    static {
        // Base resources → early tools
        reg("planks", Items.OAK_PLANKS, 4, new Item[]{Items.OAK_LOG}, new int[]{1});
        reg("sticks", Items.STICK, 4, new Item[]{Items.OAK_PLANKS}, new int[]{2});
        reg("crafting_table", Items.CRAFTING_TABLE, 1, new Item[]{Items.OAK_PLANKS}, new int[]{4});
        reg("chest", Items.CHEST, 1, new Item[]{Items.OAK_PLANKS}, new int[]{8});
        reg("furnace", Items.FURNACE, 1, new Item[]{Items.COBBLESTONE}, new int[]{8});
        reg("torch", Items.TORCH, 4, new Item[]{Items.COAL, Items.STICK}, new int[]{1, 1});

        // Wooden tools
        reg("wooden_pickaxe", Items.WOODEN_PICKAXE, 1, new Item[]{Items.OAK_PLANKS, Items.STICK}, new int[]{3, 2});
        reg("wooden_sword", Items.WOODEN_SWORD, 1, new Item[]{Items.OAK_PLANKS, Items.STICK}, new int[]{2, 1});
        reg("wooden_axe", Items.WOODEN_AXE, 1, new Item[]{Items.OAK_PLANKS, Items.STICK}, new int[]{3, 2});
        reg("wooden_shovel", Items.WOODEN_SHOVEL, 1, new Item[]{Items.OAK_PLANKS, Items.STICK}, new int[]{1, 2});

        // Stone tools
        reg("stone_pickaxe", Items.STONE_PICKAXE, 1, new Item[]{Items.COBBLESTONE, Items.STICK}, new int[]{3, 2});
        reg("stone_sword", Items.STONE_SWORD, 1, new Item[]{Items.COBBLESTONE, Items.STICK}, new int[]{2, 1});
        reg("stone_axe", Items.STONE_AXE, 1, new Item[]{Items.COBBLESTONE, Items.STICK}, new int[]{3, 2});

        // Iron tools
        reg("iron_pickaxe", Items.IRON_PICKAXE, 1, new Item[]{Items.IRON_INGOT, Items.STICK}, new int[]{3, 2});
        reg("iron_sword", Items.IRON_SWORD, 1, new Item[]{Items.IRON_INGOT, Items.STICK}, new int[]{2, 1});
        reg("iron_axe", Items.IRON_AXE, 1, new Item[]{Items.IRON_INGOT, Items.STICK}, new int[]{3, 2});
        reg("iron_helmet", Items.IRON_HELMET, 1, new Item[]{Items.IRON_INGOT}, new int[]{5});
        reg("iron_chestplate", Items.IRON_CHESTPLATE, 1, new Item[]{Items.IRON_INGOT}, new int[]{8});
        reg("iron_leggings", Items.IRON_LEGGINGS, 1, new Item[]{Items.IRON_INGOT}, new int[]{7});
        reg("iron_boots", Items.IRON_BOOTS, 1, new Item[]{Items.IRON_INGOT}, new int[]{4});
        reg("bucket", Items.BUCKET, 1, new Item[]{Items.IRON_INGOT}, new int[]{3});
        reg("shield", Items.SHIELD, 1, new Item[]{Items.OAK_PLANKS, Items.IRON_INGOT}, new int[]{6, 1});

        // Diamond tools
        reg("diamond_pickaxe", Items.DIAMOND_PICKAXE, 1, new Item[]{Items.DIAMOND, Items.STICK}, new int[]{3, 2});
        reg("diamond_sword", Items.DIAMOND_SWORD, 1, new Item[]{Items.DIAMOND, Items.STICK}, new int[]{2, 1});
        reg("diamond_axe", Items.DIAMOND_AXE, 1, new Item[]{Items.DIAMOND, Items.STICK}, new int[]{3, 2});
        reg("diamond_helmet", Items.DIAMOND_HELMET, 1, new Item[]{Items.DIAMOND}, new int[]{5});
        reg("diamond_chestplate", Items.DIAMOND_CHESTPLATE, 1, new Item[]{Items.DIAMOND}, new int[]{8});
        reg("diamond_leggings", Items.DIAMOND_LEGGINGS, 1, new Item[]{Items.DIAMOND}, new int[]{7});
        reg("diamond_boots", Items.DIAMOND_BOOTS, 1, new Item[]{Items.DIAMOND}, new int[]{4});

        // Combat & misc
        reg("bow", Items.BOW, 1, new Item[]{Items.STICK, Items.STRING}, new int[]{3, 3});
        reg("arrow", Items.ARROW, 4, new Item[]{Items.FLINT, Items.STICK, Items.FEATHER}, new int[]{1, 1, 1});
        reg("bread", Items.BREAD, 1, new Item[]{Items.WHEAT}, new int[]{3});
    }

    private static void reg(String name, Item result, int count, Item[] mats, int[] amounts) {
        RECIPES.put(name, new CraftRecipe(name, result, count, mats, amounts));
    }

    // Find recipe by name
    public static CraftRecipe getRecipe(String name) {
        return RECIPES.get(name);
    }

    // Find recipe that produces a given item
    public static CraftRecipe findRecipeFor(Item item) {
        for (CraftRecipe r : RECIPES.values()) {
            if (r.result == item) return r;
        }
        return null;
    }

    // Get all recipe names
    public static List<String> getAllRecipeNames() {
        return new ArrayList<>(RECIPES.keySet());
    }

    /**
     * Resolve FULL crafting chain from raw materials → target.
     * Returns ordered list of steps, or null if impossible (missing base resource).
     */
    public static List<CraftStep> resolveChain(String targetName, SimpleInventory inv) {
        CraftRecipe target = RECIPES.get(targetName);
        if (target == null) return null;

        List<CraftStep> steps = new ArrayList<>();
        // Simulate inventory
        int[] simInv = new int[64];
        Item[] simItems = new Item[64];
        int simSize = 0;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (!s.isEmpty()) {
                int idx = findOrAdd(simItems, simSize, s.getItem());
                if (idx >= simSize) simSize = idx + 1;
                simInv[idx] += s.getCount();
            }
        }

        if (resolveRecursive(target, steps, simItems, simInv, simSize, 0)) {
            return steps;
        }
        return null; // impossible
    }

    private static boolean resolveRecursive(CraftRecipe recipe, List<CraftStep> steps,
                                            Item[] simItems, int[] simInv, int simSize, int depth) {
        if (depth > 10) return false; // prevent infinite loops

        // Check each ingredient
        for (int i = 0; i < recipe.mats.length; i++) {
            Item mat = recipe.mats[i];
            int need = recipe.amounts[i];
            int have = countSim(simItems, simInv, simSize, mat);

            if (have < need) {
                // Try to craft the missing ingredient
                CraftRecipe subRecipe = findRecipeFor(mat);
                if (subRecipe == null) return false; // base resource missing, can't auto-craft

                int deficit = need - have;
                int batches = (deficit + subRecipe.outputCount - 1) / subRecipe.outputCount;
                for (int b = 0; b < batches; b++) {
                    if (!resolveRecursive(subRecipe, steps, simItems, simInv, simSize, depth + 1))
                        return false;
                }
            }
        }

        // Now consume ingredients
        for (int i = 0; i < recipe.mats.length; i++) {
            removeSim(simItems, simInv, simSize, recipe.mats[i], recipe.amounts[i]);
        }
        // Add output
        int idx = findOrAdd(simItems, simSize, recipe.result);
        if (idx >= simSize) simSize = idx + 1;
        simInv[idx] += recipe.outputCount;

        steps.add(new CraftStep(recipe.name, recipe.result, recipe.outputCount, recipe.mats, recipe.amounts));
        return true;
    }

    // Execute a full chain on the real inventory
    public static boolean executeChain(List<CraftStep> chain, SimpleInventory inv) {
        for (CraftStep step : chain) {
            CraftRecipe r = RECIPES.get(step.recipeName);
            if (r == null) return false;
            if (!canCraft(inv, r)) return false;
            // Consume
            for (int i = 0; i < r.mats.length; i++) consumeItem(inv, r.mats[i], r.amounts[i]);
            // Produce
            inv.addStack(new ItemStack(r.result, r.outputCount));
        }
        return true;
    }

    public static boolean canCraft(SimpleInventory inv, CraftRecipe r) {
        for (int i = 0; i < r.mats.length; i++)
            if (countItem(inv, r.mats[i]) < r.amounts[i]) return false;
        return true;
    }

    public static int countItem(SimpleInventory inv, Item item) {
        int c = 0;
        for (int i = 0; i < inv.size(); i++)
            if (inv.getStack(i).isOf(item)) c += inv.getStack(i).getCount();
        return c;
    }

    private static void consumeItem(SimpleInventory inv, Item item, int amount) {
        int left = amount;
        for (int i = 0; i < inv.size() && left > 0; i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isOf(item)) {
                int take = Math.min(left, stack.getCount());
                stack.decrement(take);
                left -= take;
            }
        }
    }

    // --- Simulation helpers ---
    private static int findOrAdd(Item[] items, int size, Item item) {
        for (int i = 0; i < size; i++) if (items[i] == item) return i;
        items[size] = item;
        return size;
    }

    private static int countSim(Item[] items, int[] counts, int size, Item item) {
        for (int i = 0; i < size; i++) if (items[i] == item) return counts[i];
        return 0;
    }

    private static void removeSim(Item[] items, int[] counts, int size, Item item, int amount) {
        for (int i = 0; i < size; i++) {
            if (items[i] == item) {
                counts[i] -= amount;
                return;
            }
        }
    }
}
