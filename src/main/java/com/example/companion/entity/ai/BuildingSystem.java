package com.example.companion.entity.ai;

import com.example.companion.entity.CompanionEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * Building system with blueprint-based construction.
 * Supports layer-by-layer building and material validation.
 */
public class BuildingSystem {

    // ── Blueprint data ──────────────────────────────────────────────────────

    /**
     * A complete blueprint: name, layers, and required materials.
     */
    public record Blueprint(String name, BlueprintLayer[] layers, Map<Item, Integer> materials) {}

    /**
     * One horizontal layer of a blueprint.
     * pattern[z][x], mapping char → Block.
     */
    public record BlueprintLayer(int yOffset, char[][] pattern, Map<Character, Block> mapping) {}

    /**
     * Tracks an ongoing building task.
     */
    public static class BuildingTask {
        public final Blueprint blueprint;
        public final BlockPos startPos;
        public int currentLayer = 0;
        public int currentIndex = 0; // linear index within current layer
        public boolean completed = false;

        public BuildingTask(Blueprint blueprint, BlockPos startPos) {
            this.blueprint = blueprint;
            this.startPos = startPos;
        }
    }

    // ── Pre-built blueprints ────────────────────────────────────────────────

    private static final Map<String, Blueprint> BLUEPRINTS = new LinkedHashMap<>();

    static {
        // ─── Starter house 5×5 ───
        BLUEPRINTS.put("starter_house", new Blueprint(
                "Стартовый домик 5x5",
                new BlueprintLayer[]{
                        // Layer 0 — floor
                        layer(0, new String[]{
                                "PPPPP",
                                "PPPPP",
                                "PPPPP",
                                "PPPPP",
                                "PPPPP"
                        }, Map.of('P', Blocks.OAK_PLANKS)),

                        // Layer 1 — walls + door
                        layer(1, new String[]{
                                "WWDWW",
                                "W...W",
                                "W...W",
                                "W...W",
                                "WWWWW"
                        }, Map.of('W', Blocks.OAK_PLANKS, 'D', Blocks.OAK_DOOR, '.', Blocks.AIR)),

                        // Layer 2 — walls + windows
                        layer(2, new String[]{
                                "WWWWW",
                                "W...W",
                                "G...G",
                                "W...W",
                                "WWWWW"
                        }, Map.of('W', Blocks.OAK_PLANKS, 'G', Blocks.GLASS_PANE, '.', Blocks.AIR)),

                        // Layer 3 — walls
                        layer(3, new String[]{
                                "WWWWW",
                                "W...W",
                                "W...W",
                                "W...W",
                                "WWWWW"
                        }, Map.of('W', Blocks.OAK_PLANKS, '.', Blocks.AIR)),

                        // Layer 4 — roof
                        layer(4, new String[]{
                                "SSSSS",
                                "SSSSS",
                                "SSSSS",
                                "SSSSS",
                                "SSSSS"
                        }, Map.of('S', Blocks.OAK_SLAB))
                },
                Map.of(
                        Items.OAK_PLANKS, 60,
                        Items.OAK_DOOR, 1,
                        Items.GLASS_PANE, 2
                )
        ));

        // ─── Quick shelter 3×3 ───
        BLUEPRINTS.put("shelter", new Blueprint(
                "Быстрое убежище 3x3",
                new BlueprintLayer[]{
                        layer(0, new String[]{
                                "CCC",
                                "C.C",
                                "CCC"
                        }, Map.of('C', Blocks.COBBLESTONE, '.', Blocks.AIR)),
                        layer(1, new String[]{
                                "CDC",
                                "C.C",
                                "CCC"
                        }, Map.of('C', Blocks.COBBLESTONE, 'D', Blocks.OAK_DOOR, '.', Blocks.AIR)),
                        layer(2, new String[]{
                                "CCC",
                                "C.C",
                                "CCC"
                        }, Map.of('C', Blocks.COBBLESTONE, '.', Blocks.AIR)),
                        layer(3, new String[]{
                                "CCC",
                                "CCC",
                                "CCC"
                        }, Map.of('C', Blocks.COBBLESTONE))
                },
                Map.of(
                        Items.COBBLESTONE, 32,
                        Items.OAK_DOOR, 1
                )
        ));

        // ─── Watch tower 3×3 ───
        BLUEPRINTS.put("tower", new Blueprint(
                "Сторожевая башня 3x3",
                new BlueprintLayer[]{
                        // Base floor
                        layer(0, new String[]{
                                "SSS",
                                "S.S",
                                "SSS"
                        }, Map.of('S', Blocks.STONE_BRICKS, '.', Blocks.AIR)),
                        // Walls 1
                        layer(1, new String[]{
                                "SDS",
                                "S.S",
                                "SSS"
                        }, Map.of('S', Blocks.STONE_BRICKS, 'D', Blocks.OAK_DOOR, '.', Blocks.AIR)),
                        // Walls 2
                        layer(2, new String[]{
                                "SSS",
                                "S.S",
                                "SSS"
                        }, Map.of('S', Blocks.STONE_BRICKS, '.', Blocks.AIR)),
                        // Walls 3
                        layer(3, new String[]{
                                "SSS",
                                "S.S",
                                "SSS"
                        }, Map.of('S', Blocks.STONE_BRICKS, '.', Blocks.AIR)),
                        // Walls 4
                        layer(4, new String[]{
                                "SSS",
                                "S.S",
                                "SSS"
                        }, Map.of('S', Blocks.STONE_BRICKS, '.', Blocks.AIR)),
                        // Platform floor
                        layer(5, new String[]{
                                "SSS",
                                "SSS",
                                "SSS"
                        }, Map.of('S', Blocks.STONE_BRICK_SLAB)),
                        // Parapet
                        layer(6, new String[]{
                                "S.S",
                                "...",
                                "S.S"
                        }, Map.of('S', Blocks.STONE_BRICK_WALL, '.', Blocks.AIR))
                },
                Map.of(
                        Items.STONE_BRICKS, 50,
                        Items.OAK_DOOR, 1
                )
        ));
    }

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Get available blueprint names.
     */
    public static List<String> getAvailableBlueprints() {
        return new ArrayList<>(BLUEPRINTS.keySet());
    }

    /**
     * Try to match natural-language input to a blueprint key.
     */
    public static String findBlueprint(String message) {
        if (message == null) return null;
        String low = message.toLowerCase();
        if (low.contains("дом") || low.contains("house") || low.contains("домик") || low.contains("starter")) return "starter_house";
        if (low.contains("убежищ") || low.contains("shelter") || low.contains("быстр") || low.contains("шалаш")) return "shelter";
        if (low.contains("башн") || low.contains("tower") || low.contains("вышк")) return "tower";
        // direct key match
        for (String key : BLUEPRINTS.keySet()) {
            if (low.contains(key)) return key;
        }
        return null;
    }

    /**
     * Create a new BuildingTask (does not start building yet).
     */
    public static BuildingTask createTask(String blueprintKey, BlockPos startPos) {
        Blueprint bp = BLUEPRINTS.get(blueprintKey);
        if (bp == null) return null;
        return new BuildingTask(bp, startPos);
    }

    /**
     * Calculate missing materials for a building task.
     */
    public static Map<Item, Integer> getMissingMaterials(BuildingTask task, SimpleInventory inv) {
        Map<Item, Integer> missing = new LinkedHashMap<>();
        for (var entry : task.blueprint.materials.entrySet()) {
            int have = countItem(inv, entry.getKey());
            if (have < entry.getValue()) {
                missing.put(entry.getKey(), entry.getValue() - have);
            }
        }
        return missing;
    }

    /**
     * Format missing materials for display.
     */
    public static String formatMissing(Map<Item, Integer> missing) {
        StringBuilder sb = new StringBuilder();
        for (var entry : missing.entrySet()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(entry.getValue()).append("x ").append(entry.getKey().getName().getString());
        }
        return sb.toString();
    }

    /**
     * Execute up to `maxBlocks` placement steps. Returns true when fully complete.
     */
    public static boolean executeStep(CompanionEntity entity, BuildingTask task, int maxBlocks) {
        if (task.completed) return true;
        World world = entity.getWorld();
        if (world.isClient()) return false;
        Blueprint bp = task.blueprint;

        int placed = 0;
        while (placed < maxBlocks && task.currentLayer < bp.layers.length) {
            BlueprintLayer layer = bp.layers[task.currentLayer];
            int depth = layer.pattern.length;
            int width = depth > 0 ? layer.pattern[0].length : 0;
            int totalInLayer = depth * width;

            while (task.currentIndex < totalInLayer && placed < maxBlocks) {
                int z = task.currentIndex / width;
                int x = task.currentIndex % width;
                task.currentIndex++;

                char symbol = layer.pattern[z][x];
                Block block = layer.mapping.getOrDefault(symbol, Blocks.AIR);
                if (block == Blocks.AIR) continue;

                BlockPos pos = task.startPos.add(x, layer.yOffset, z);

                // Consume material from inventory if possible
                Item blockItem = block.asItem();
                if (blockItem != Items.AIR) {
                    SimpleInventory inv = entity.getCompanionInventory();
                    boolean consumed = consumeItem(inv, blockItem, 1);
                    if (!consumed) {
                        // Try to keep going; maybe we gave creative materials
                    }
                }

                // Place the block
                if (block instanceof DoorBlock) {
                    // Place lower half
                    world.setBlockState(pos, block.getDefaultState().with(DoorBlock.HALF, DoubleBlockHalf.LOWER), 3);
                    world.setBlockState(pos.up(), block.getDefaultState().with(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
                } else {
                    world.setBlockState(pos, block.getDefaultState(), 3);
                }

                // Sound
                BlockState state = block.getDefaultState();
                world.playSound(null, pos,
                        state.getSoundGroup().getPlaceSound(),
                        SoundCategory.BLOCKS, 0.7F, 1.0F);

                placed++;
            }

            if (task.currentIndex >= totalInLayer) {
                task.currentLayer++;
                task.currentIndex = 0;
            }
        }

        if (task.currentLayer >= bp.layers.length) {
            task.completed = true;
            return true;
        }
        return false;
    }

    /**
     * Validate and place a single block (for place_block action).
     * Returns true if placed successfully.
     */
    public static boolean validateAndPlace(CompanionEntity entity, BlockPos pos, Item item) {
        World world = entity.getWorld();
        if (world.isClient()) return false;

        // Must be a BlockItem
        if (!(item instanceof BlockItem bi)) {
            return false;
        }

        Block block = bi.getBlock();

        // Check inventory
        SimpleInventory inv = entity.getCompanionInventory();
        if (countItem(inv, item) <= 0) {
            return false;
        }

        // Check if position is replaceable
        if (!world.getBlockState(pos).isReplaceable()) {
            return false;
        }

        // Place
        if (block instanceof DoorBlock) {
            world.setBlockState(pos, block.getDefaultState().with(DoorBlock.HALF, DoubleBlockHalf.LOWER), 3);
            world.setBlockState(pos.up(), block.getDefaultState().with(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
        } else {
            world.setBlockState(pos, block.getDefaultState(), 3);
        }

        // Consume
        consumeItem(inv, item, 1);

        // Sound
        BlockState state = block.getDefaultState();
        world.playSound(null, pos,
                state.getSoundGroup().getPlaceSound(),
                SoundCategory.BLOCKS, 1.0F, 1.0F);

        return true;
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static BlueprintLayer layer(int yOffset, String[] rows, Map<Character, Block> mapping) {
        char[][] pattern = new char[rows.length][];
        for (int i = 0; i < rows.length; i++) {
            pattern[i] = rows[i].toCharArray();
        }
        return new BlueprintLayer(yOffset, pattern, mapping);
    }

    private static int countItem(SimpleInventory inv, Item item) {
        int c = 0;
        for (int i = 0; i < inv.size(); i++)
            if (inv.getStack(i).isOf(item)) c += inv.getStack(i).getCount();
        return c;
    }

    private static boolean consumeItem(SimpleInventory inv, Item item, int amount) {
        int left = amount;
        for (int i = 0; i < inv.size() && left > 0; i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isOf(item)) {
                int take = Math.min(left, stack.getCount());
                stack.decrement(take);
                left -= take;
            }
        }
        return left <= 0;
    }
}
