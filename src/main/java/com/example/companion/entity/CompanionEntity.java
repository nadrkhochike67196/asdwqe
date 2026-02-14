package com.example.companion.entity;

import com.example.companion.entity.ai.*;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.UUID;

public class CompanionEntity extends PathAwareEntity {
    private UUID ownerUuid;
    private final AIBrain aiBrain = new AIBrain();
    private final MemorySystem memory = new MemorySystem();
    private final SimpleInventory inventory = new SimpleInventory(27);
    private int aiTickCounter;
    private String currentAction = "idle";

    public CompanionEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(1, new MeleeAttackGoal(this, 1.2, true));
        this.goalSelector.add(2, new com.example.companion.entity.ai.FollowOwnerGoal(this, 1.0, 10.0, 3.0));
        this.goalSelector.add(3, new LookAtEntityGoal(this, PlayerEntity.class, 8.0F));
        this.goalSelector.add(4, new WanderAroundFarGoal(this, 0.8));
        this.targetSelector.add(1, new ActiveTargetGoal<>(this, HostileEntity.class, true));
        this.targetSelector.add(2, new RevengeGoal(this));
    }

    public static DefaultAttributeContainer.Builder createCompanionAttributes() {
        return PathAwareEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 60.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.35)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 8.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48.0)
                .add(EntityAttributes.GENERIC_ARMOR, 4.0);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient() || ownerUuid == null) return;

        // Auto-pickup nearby items
        autoPickup();

        // AI decision every 5 seconds
        aiTickCounter++;
        if (aiTickCounter >= 100) {
            aiTickCounter = 0;
            queryAI();
        }
    }

    private void autoPickup() {
        var items = this.getWorld().getEntitiesByClass(ItemEntity.class,
                this.getBoundingBox().expand(2.0), e -> true);
        for (ItemEntity ie : items) {
            ItemStack rem = inventory.addStack(ie.getStack().copy());
            if (rem.isEmpty()) ie.discard();
            else ie.setStack(rem);
        }
    }

    private void queryAI() {
        String context = WorldPerceptionSystem.buildContext(this);
        aiBrain.think(context).thenAccept(action -> {
            if (this.getWorld() instanceof ServerWorld sw) {
                sw.getServer().execute(() -> processAction(action));
            }
        });
    }

    public void onPlayerChat(PlayerEntity player, String message) {
        if (this.getWorld().isClient()) return;
        String context = WorldPerceptionSystem.buildContext(this);
        aiBrain.chat(player.getName().getString(), message, context).thenAccept(action -> {
            if (this.getWorld() instanceof ServerWorld sw) {
                sw.getServer().execute(() -> processAction(action));
            }
        });
    }

    private void processAction(AIBrain.AIAction action) {
        this.currentAction = action.action();

        // Broadcast message
        if (action.message() != null && !action.message().isEmpty()) {
            if (this.getWorld() instanceof ServerWorld sw) {
                sw.getServer().getPlayerManager().broadcast(
                        Text.literal("\u00A7b[Buddy] \u00A7f" + action.message()), false);
            }
        }

        // Remember interesting blocks from mining actions
        if ("mine".equals(action.action()) && action.x() != 0) {
            BlockPos bp = new BlockPos(action.x(), action.y(), action.z());
            String name = this.getWorld().getBlockState(bp).getBlock().getName().getString();
            memory.remember(name, bp);
        }

        // Execute action
        ActionExecutor.execute(this, action);
    }

    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (!this.getWorld().isClient()) {
            if (ownerUuid == null) {
                this.ownerUuid = player.getUuid();
                this.playSound(SoundEvents.ENTITY_VILLAGER_YES, 1.0F, 1.0F);
                player.sendMessage(Text.literal("\u00A7a[Buddy] \u00A7fI'm your companion now! Talk to me in chat!"), false);
                return ActionResult.SUCCESS;
            } else if (ownerUuid.equals(player.getUuid())) {
                player.sendMessage(Text.literal(
                        "\u00A7e[Buddy] \u00A7fAction: " + currentAction +
                        " | HP: " + (int) getHealth() +
                        " | Items: " + countItems()), false);
                return ActionResult.SUCCESS;
            }
        }
        return super.interactMob(player, hand);
    }

    private int countItems() {
        int c = 0;
        for (int i = 0; i < inventory.size(); i++)
            if (!inventory.getStack(i).isEmpty()) c++;
        return c;
    }

    // Accessors
    public SimpleInventory getCompanionInventory() { return inventory; }
    public MemorySystem getMemory() { return memory; }
    public UUID getOwnerUuid() { return ownerUuid; }

    public PlayerEntity getOwnerPlayer() {
        if (ownerUuid == null) return null;
        if (this.getWorld() instanceof ServerWorld sw)
            return sw.getServer().getPlayerManager().getPlayer(ownerUuid);
        return null;
    }

    @Override
    public boolean canTarget(LivingEntity target) {
        if (target instanceof PlayerEntity p && ownerUuid != null)
            return !p.getUuid().equals(ownerUuid);
        return super.canTarget(target);
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (ownerUuid != null) nbt.putUuid("OwnerUUID", ownerUuid);
        nbt.put("Memory", memory.toNbt());
        // Save inventory
        NbtList invList = new NbtList();
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (!stack.isEmpty()) {
                NbtCompound item = new NbtCompound();
                item.putByte("Slot", (byte) i);
                stack.writeNbt(item);
                invList.add(item);
            }
        }
        nbt.put("Inventory", invList);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.containsUuid("OwnerUUID")) ownerUuid = nbt.getUuid("OwnerUUID");
        if (nbt.contains("Memory")) memory.fromNbt(nbt.getCompound("Memory"));
        if (nbt.contains("Inventory")) {
            NbtList invList = nbt.getList("Inventory", 10);
            for (int i = 0; i < invList.size(); i++) {
                NbtCompound item = invList.getCompound(i);
                int slot = item.getByte("Slot") & 255;
                if (slot < inventory.size())
                    inventory.setStack(slot, ItemStack.fromNbt(item));
            }
        }
    }
}
