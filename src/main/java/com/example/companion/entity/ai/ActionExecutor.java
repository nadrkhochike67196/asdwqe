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
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

public class ActionExecutor {

    private record Recipe(String name, Item result, int count, Item[] mats, int[] amounts) {}

    private static final List<Recipe> RECIPES = List.of(
        new Recipe("planks", Items.OAK_PLANKS, 4, new Item[]{Items.OAK_LOG}, new int[]{1}),
        new Recipe("sticks", Items.STICK, 4, new Item[]{Items.OAK_PLANKS}, new int[]{2}),
        new Recipe("crafting_table", Items.CRAFTING_TABLE, 1, new Item[]{Items.OAK_PLANKS}, new int[]{4}),
        new Recipe("wooden_pickaxe", Items.WOODEN_PICKAXE, 1, new Item[]{Items.OAK_PLANKS, Items.STICK}, new int[]{3, 2}),
        new Recipe("wooden_sword", Items.WOODEN_SWORD, 1, new Item[]{Items.OAK_PLANKS, Items.STICK}, new int[]{2, 1}),
        new Recipe("stone_pickaxe", Items.STONE_PICKAXE, 1, new Item[]{Items.COBBLESTONE, Items.STICK}, new int[]{3, 2}),
        new Recipe("stone_sword", Items.STONE_SWORD, 1, new Item[]{Items.COBBLESTONE, Items.STICK}, new int[]{2, 1}),
        new Recipe("iron_pickaxe", Items.IRON_PICKAXE, 1, new Item[]{Items.IRON_INGOT, Items.STICK}, new int[]{3, 2}),
        new Recipe("iron_sword", Items.IRON_SWORD, 1, new Item[]{Items.IRON_INGOT, Items.STICK}, new int[]{2, 1}),
        new Recipe("diamond_pickaxe", Items.DIAMOND_PICKAXE, 1, new Item[]{Items.DIAMOND, Items.STICK}, new int[]{3, 2}),
        new Recipe("diamond_sword", Items.DIAMOND_SWORD, 1, new Item[]{Items.DIAMOND, Items.STICK}, new int[]{2, 1}),
        new Recipe("furnace", Items.FURNACE, 1, new Item[]{Items.COBBLESTONE}, new int[]{8}),
        new Recipe("torch", Items.TORCH, 4, new Item[]{Items.COAL, Items.STICK}, new int[]{1, 1})
    );

    public static void execute(CompanionEntity entity, AIBrain.AIAction action) {
        switch (action.action()) {
            case "mine" -> executeMine(entity, new BlockPos(action.x(), action.y(), action.z()));
            case "move" -> executeMove(entity, new BlockPos(action.x(), action.y(), action.z()));
            case "attack" -> executeAttack(entity);
            case "pickup" -> executePickup(entity);
            case "eat" -> executeEat(entity);
            case "craft" -> executeCraft(entity, action.target());
            case "place" -> executePlace(entity, new BlockPos(action.x(), action.y(), action.z()));
            case "give_player" -> executeGive(entity);
            case "say" -> { /* message already broadcast by entity */ }
            case "follow" -> executeFollow(entity);
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

    public static boolean executeCraft(CompanionEntity entity, String recipeName) {
        if (recipeName == null) return false;
        SimpleInventory inv = entity.getCompanionInventory();
        for (Recipe r : RECIPES) {
            if (r.name.equals(recipeName) && canCraft(inv, r)) {
                for (int i = 0; i < r.mats.length; i++) consumeItem(inv, r.mats[i], r.amounts[i]);
                inv.addStack(new ItemStack(r.result, r.count));
                entity.playSound(SoundEvents.BLOCK_WOODEN_BUTTON_CLICK_ON, 1.0F, 1.5F);
                return true;
            }
        }
        entity.getMemory().addFailure("craft_failed:" + recipeName);
        return false;
    }

    public static void executePlace(CompanionEntity entity, BlockPos pos) {
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

    private static boolean canCraft(SimpleInventory inv, Recipe r) {
        for (int i = 0; i < r.mats.length; i++)
            if (countItem(inv, r.mats[i]) < r.amounts[i]) return false;
        return true;
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
}
