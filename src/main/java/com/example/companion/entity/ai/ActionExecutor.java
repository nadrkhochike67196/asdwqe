package com.example.companion.entity.ai;

import com.example.companion.entity.CompanionEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;
import java.util.Map;

public class ActionExecutor {

    public static void execute(CompanionEntity entity, AIBrain.AIAction action) {
        switch (action.action()) {
            case "mine" -> executeMine(entity, new BlockPos(action.x(), action.y(), action.z()));
            case "move" -> executeMove(entity, new BlockPos(action.x(), action.y(), action.z()));
            case "attack" -> executeAttack(entity);
            case "pickup" -> executePickup(entity);
            case "eat" -> executeEat(entity);
            case "craft" -> executeCraftWithChain(entity, action.target());
            case "place" -> executePlaceSmart(entity, new BlockPos(action.x(), action.y(), action.z()), action.target());
            case "place_block" -> executePlaceSpecific(entity, new BlockPos(action.x(), action.y(), action.z()), action.target());
            case "build" -> executeBuild(entity, action.target());
            case "give_player" -> executeGive(entity);
            case "say" -> { /* message already broadcast by entity */ }
            case "follow" -> executeFollow(entity);
            case "teleport" -> executeTeleportToOwner(entity);
            default -> { }
        }
    }

    public static void executeMine(CompanionEntity entity, BlockPos pos) {
        World world = entity.getWorld();
        if (world.isClient() || world.getBlockState(pos).isAir()) return;
        double dist = Math.sqrt(entity.getBlockPos().getSquaredDistance(pos));
        if (dist < 5.0) {
            world.breakBlock(pos, true);
            entity.swingHand(Hand.MAIN_HAND);
            entity.playSound(SoundEvents.BLOCK_STONE_BREAK, 1.0F, 1.0F);
        } else {
            entity.getNavigation().startMovingTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 1.0);
        }
    }

    public static void executeMove(CompanionEntity entity, BlockPos pos) {
        entity.getNavigation().startMovingTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 1.0);
    }

    public static void executeAttack(CompanionEntity entity) {
        var hostiles = new java.util.ArrayList<>(entity.getWorld().getEntitiesByClass(HostileEntity.class,
                entity.getBoundingBox().expand(16.0), e -> true));
        if (!hostiles.isEmpty()) {
            hostiles.sort((a, b) -> Double.compare(entity.distanceTo(a), entity.distanceTo(b)));
            entity.setTarget(hostiles.get(0));
        }
    }

    public static void executePickup(CompanionEntity entity) {
        var items = entity.getWorld().getEntitiesByClass(ItemEntity.class,
                entity.getBoundingBox().expand(3.0), e -> true);
        SimpleInventory inv = entity.getCompanionInventory();
        for (ItemEntity ie : items) {
            ItemStack remainder = inv.addStack(ie.getStack().copy());
            if (remainder.isEmpty()) {
                ie.discard();
            } else {
                ie.setStack(remainder);
            }
        }
    }

    public static void executeEat(CompanionEntity entity) {
        if (entity.getHealth() < entity.getMaxHealth()) {
            entity.heal(6.0F);
            entity.playSound(SoundEvents.ENTITY_GENERIC_EAT, 1.0F, 1.0F);
        }
    }

    /**
     * Smart crafting with full chain resolution.
     * If target needs sub-crafts (e.g., crafting_table needs planks), resolves entire chain.
     */
    public static boolean executeCraftWithChain(CompanionEntity entity, String recipeName) {
        if (recipeName == null) return false;
        SimpleInventory inv = entity.getCompanionInventory();

        // Try to resolve the full chain
        List<CraftingChainResolver.CraftStep> chain = CraftingChainResolver.resolveChain(recipeName, inv);

        if (chain != null && !chain.isEmpty()) {
            // Execute full chain
            boolean success = CraftingChainResolver.executeChain(chain, inv);
            if (success) {
                entity.playSound(SoundEvents.BLOCK_WOODEN_BUTTON_CLICK_ON, 1.0F, 1.5F);
                // Broadcast what was crafted
                if (chain.size() > 1) {
                    broadcastMsg(entity, PersonalitySystem.formatMessage(
                            "Скрафтил цепочку: " + chain.size() + " шагов → " + recipeName,
                            PersonalitySystem.ResponseStyle.SMUG));
                }
                return true;
            }
        }

        // Direct craft fallback (maybe chain resolution failed but direct craft works)
        CraftingChainResolver.CraftRecipe recipe = CraftingChainResolver.getRecipe(recipeName);
        if (recipe != null && CraftingChainResolver.canCraft(inv, recipe)) {
            for (int i = 0; i < recipe.mats().length; i++) {
                consumeItem(inv, recipe.mats()[i], recipe.amounts()[i]);
            }
            inv.addStack(new ItemStack(recipe.result(), recipe.outputCount()));
            entity.playSound(SoundEvents.BLOCK_WOODEN_BUTTON_CLICK_ON, 1.0F, 1.5F);
            return true;
        }

        // Report what's missing
        if (recipe != null) {
            StringBuilder missing = new StringBuilder();
            for (int i = 0; i < recipe.mats().length; i++) {
                int have = CraftingChainResolver.countItem(inv, recipe.mats()[i]);
                int need = recipe.amounts()[i];
                if (have < need) {
                    if (!missing.isEmpty()) missing.append(", ");
                    missing.append((need - have)).append("x ").append(recipe.mats()[i].getName().getString());
                }
            }
            broadcastMsg(entity, PersonalitySystem.formatMessage(
                    "Не могу скрафтить " + recipeName + ". Не хватает: " + missing,
                    PersonalitySystem.ResponseStyle.ANNOYED));
        }

        entity.getMemory().addFailure("craft_failed:" + recipeName);
        return false;
    }

    /**
     * Smart place: validates the block being placed is correct
     */
    public static void executePlaceSmart(CompanionEntity entity, BlockPos pos, String targetItemName) {
        if (targetItemName != null && !targetItemName.isEmpty()) {
            // Try to find specific item
            Item targetItem = findItemByName(targetItemName);
            if (targetItem != null) {
                if (BuildingSystem.validateAndPlace(entity, pos, targetItem)) return;
                broadcastMsg(entity, PersonalitySystem.formatMessage(
                        "Не могу поставить " + targetItemName,
                        PersonalitySystem.ResponseStyle.ANNOYED));
                return;
            }
        }

        // Fallback: place first available block
        SimpleInventory inv = entity.getCompanionInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem bi) {
                entity.getWorld().setBlockState(pos, bi.getBlock().getDefaultState());
                stack.decrement(1);
                entity.swingHand(Hand.MAIN_HAND);
                return;
            }
        }
    }

    /**
     * Place a specific item by its registry name (e.g., "minecraft:crafting_table")
     */
    public static void executePlaceSpecific(CompanionEntity entity, BlockPos pos, String itemId) {
        if (itemId == null) return;
        Item item = findItemByName(itemId);
        if (item == null) {
            broadcastMsg(entity, PersonalitySystem.formatMessage(
                    "Не знаю что такое " + itemId,
                    PersonalitySystem.ResponseStyle.ANNOYED));
            return;
        }

        double dist = Math.sqrt(entity.getBlockPos().getSquaredDistance(pos));
        if (dist < 5.0) {
            if (!BuildingSystem.validateAndPlace(entity, pos, item)) {
                broadcastMsg(entity, PersonalitySystem.formatMessage(
                        "Не удалось поставить " + item.getName().getString(),
                        PersonalitySystem.ResponseStyle.ANNOYED));
            }
        } else {
            entity.getNavigation().startMovingTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 1.0);
        }
    }

    /**
     * Start or continue building from a blueprint
     */
    public static void executeBuild(CompanionEntity entity, String blueprintName) {
        if (blueprintName == null) return;

        // Resolve blueprint name from natural language
        String key = BuildingSystem.findBlueprint(blueprintName);
        if (key == null) key = blueprintName;

        // Check if entity already has an active building task
        BuildingSystem.BuildingTask activeTask = entity.getBuildingTask();

        if (activeTask != null && !activeTask.completed) {
            // Continue existing task
            boolean done = BuildingSystem.executeStep(entity, activeTask, 5);
            if (done) {
                entity.setBuildingTask(null);
                broadcastMsg(entity, PersonalitySystem.formatMessage(
                        "ГОТОВО! Построил " + activeTask.blueprint.name(),
                        PersonalitySystem.ResponseStyle.EXCITED));
            }
            return;
        }

        // Create new task
        PlayerEntity owner = entity.getOwnerPlayer();
        BlockPos startPos;
        if (owner != null) {
            // Build 5 blocks in front of the player
            startPos = owner.getBlockPos().offset(owner.getHorizontalFacing(), 5);
        } else {
            startPos = entity.getBlockPos().offset(entity.getHorizontalFacing(), 3);
        }

        BuildingSystem.BuildingTask task = BuildingSystem.createTask(key, startPos);
        if (task == null) {
            broadcastMsg(entity, PersonalitySystem.formatMessage(
                    "Хз что такое " + blueprintName + ". Могу построить: " +
                            String.join(", ", BuildingSystem.getAvailableBlueprints()),
                    PersonalitySystem.ResponseStyle.ANNOYED));
            return;
        }

        // Check materials
        Map<Item, Integer> missing = BuildingSystem.getMissingMaterials(task, entity.getCompanionInventory());
        if (!missing.isEmpty()) {
            broadcastMsg(entity, PersonalitySystem.formatMessage(
                    "Нужны материалы для " + task.blueprint.name() + ": " +
                            BuildingSystem.formatMissing(missing),
                    PersonalitySystem.ResponseStyle.ANNOYED));
            return;
        }

        entity.setBuildingTask(task);
        broadcastMsg(entity, PersonalitySystem.formatMessage(
                "Окей, строю " + task.blueprint.name() + "!",
                PersonalitySystem.ResponseStyle.EXCITED));

        // Start building
        BuildingSystem.executeStep(entity, task, 5);
    }

    /**
     * Teleport to owner
     */
    public static void executeTeleportToOwner(CompanionEntity entity) {
        PlayerEntity owner = entity.getOwnerPlayer();
        if (owner != null) {
            PlayerCommandSystem.teleportToPlayer(entity, owner);
            broadcastMsg(entity, PersonalitySystem.formatMessage(
                    "Телепортнулся! Что надо?",
                    PersonalitySystem.ResponseStyle.CASUAL));
        }
    }

    /**
     * Find an item by name (supports registry IDs and recipe names)
     */
    private static Item findItemByName(String name) {
        if (name == null) return null;
        // Try registry ID first
        String cleaned = name.trim().toLowerCase();
        if (!cleaned.contains(":")) cleaned = "minecraft:" + cleaned;
        try {
            Identifier id = new Identifier(cleaned);
            Item item = Registries.ITEM.get(id);
            if (item != Items.AIR) return item;
        } catch (Exception ignored) {}

        // Try matching by recipe name in CraftingChainResolver
        CraftingChainResolver.CraftRecipe recipe = CraftingChainResolver.getRecipe(name);
        if (recipe != null) return recipe.result();

        return null;
    }

    public static void executeGive(CompanionEntity entity) {
        PlayerEntity owner = entity.getOwnerPlayer();
        if (owner == null) return;
        if (entity.distanceTo(owner) > 4.0) {
            entity.getNavigation().startMovingTo(owner, 1.2);
            return;
        }
        SimpleInventory inv = entity.getCompanionInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (!stack.isEmpty()) {
                owner.getInventory().insertStack(stack.copy());
                inv.setStack(i, ItemStack.EMPTY);
                entity.playSound(SoundEvents.ENTITY_ITEM_PICKUP, 1.0F, 1.0F);
                return;
            }
        }
    }

    public static void executeFollow(CompanionEntity entity) {
        PlayerEntity owner = entity.getOwnerPlayer();
        if (owner != null) entity.getNavigation().startMovingTo(owner, 1.0);
    }

    private static int countItem(SimpleInventory inv, Item item) {
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

    private static void broadcastMsg(CompanionEntity entity, String msg) {
        if (msg == null || msg.isEmpty()) return;
        if (entity.getWorld() instanceof ServerWorld sw) {
            sw.getServer().getPlayerManager().broadcast(
                    Text.literal("\u00A7b[Buddy] \u00A7f" + msg), false);
        }
    }
}
