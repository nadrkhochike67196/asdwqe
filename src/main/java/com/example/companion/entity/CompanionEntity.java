package com.example.companion.entity;

import com.example.companion.entity.ai.AIBrain;
import com.example.companion.entity.ai.FollowOwnerGoal;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
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
    private final AIBrain aiBrain;
    private int aiTickCounter;
    private String currentAction = "IDLE";
    private BlockPos targetMinePos;

    public CompanionEntity(EntityType<? extends PathAwareEntity> entityType, World world) {
        super(entityType, world);
        this.aiBrain = new AIBrain();
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(1, new MeleeAttackGoal(this, 1.2, true));
        this.goalSelector.add(2, new FollowOwnerGoal(this, 1.0, 10.0, 3.0));
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
        if (!this.getWorld().isClient() && ownerUuid != null) {
            aiTickCounter++;
            if (aiTickCounter >= 100) { // every 5 seconds
                aiTickCounter = 0;
                queryAI();
            }
            executeCurrentAction();
        }
    }

    private void queryAI() {
        String gameState = aiBrain.collectGameState(this);
        aiBrain.think(gameState).thenAccept(action -> {
            if (this.getWorld() instanceof ServerWorld serverWorld) {
                serverWorld.getServer().execute(() -> processAIResponse(action));
            }
        });
    }

    /** Called when a player chats with the companion */
    public void onPlayerChat(PlayerEntity player, String message) {
        if (this.getWorld().isClient()) return;
        String gameState = aiBrain.collectGameState(this);
        aiBrain.chat(player.getName().getString(), message, gameState).thenAccept(action -> {
            if (this.getWorld() instanceof ServerWorld serverWorld) {
                serverWorld.getServer().execute(() -> processAIResponse(action));
            }
        });
    }

    private void processAIResponse(AIBrain.AIAction action) {
        this.currentAction = action.action();

        // Always broadcast the say message
        if (action.message() != null && !action.message().isEmpty()) {
            if (this.getWorld() instanceof ServerWorld serverWorld) {
                serverWorld.getServer().getPlayerManager().broadcast(
                        Text.literal("\u00A7b[Buddy] \u00A7f" + action.message()), false);
            }
        }

        switch (action.action()) {
            case "ATTACK" -> {
                var hostile = this.getWorld().getClosestEntity(
                        HostileEntity.class,
                        net.minecraft.entity.ai.TargetPredicate.DEFAULT,
                        this, this.getX(), this.getY(), this.getZ(),
                        this.getBoundingBox().expand(16.0)
                );
                if (hostile != null) this.setTarget(hostile);
            }
            case "MINE" -> {
                if (action.x() != 0 || action.y() != 0 || action.z() != 0) {
                    this.targetMinePos = new BlockPos(action.x(), action.y(), action.z());
                }
            }
            case "EAT" -> {
                if (this.getHealth() < this.getMaxHealth()) {
                    this.heal(6.0F);
                    this.playSound(SoundEvents.ENTITY_GENERIC_EAT, 1.0F, 1.0F);
                }
            }
            default -> { /* FOLLOW, IDLE, SPEAK handled by goals or no-op */ }
        }
    }

    private void executeCurrentAction() {
        if ("MINE".equals(currentAction) && targetMinePos != null) {
            double dist = Math.sqrt(this.getBlockPos().getSquaredDistance(targetMinePos));
            if (dist < 4.0) {
                if (!this.getWorld().getBlockState(targetMinePos).isAir()) {
                    this.getWorld().breakBlock(targetMinePos, true);
                    this.playSound(SoundEvents.BLOCK_STONE_BREAK, 1.0F, 1.0F);
                }
                targetMinePos = null;
                currentAction = "IDLE";
            } else {
                this.getNavigation().startMovingTo(
                        targetMinePos.getX(), targetMinePos.getY(), targetMinePos.getZ(), 1.0);
            }
        }
    }

    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (!this.getWorld().isClient()) {
            if (ownerUuid == null) {
                this.ownerUuid = player.getUuid();
                this.playSound(SoundEvents.ENTITY_VILLAGER_YES, 1.0F, 1.0F);
                player.sendMessage(Text.literal("\u00A7a[Buddy] I am now your companion! Talk to me in chat!"), false);
                return ActionResult.SUCCESS;
            } else if (ownerUuid.equals(player.getUuid())) {
                player.sendMessage(Text.literal("\u00A7e[Buddy] Action: " + currentAction), false);
                return ActionResult.SUCCESS;
            }
        }
        return super.interactMob(player, hand);
    }

    public PlayerEntity getOwnerPlayer() {
        if (ownerUuid == null) return null;
        if (this.getWorld() instanceof ServerWorld serverWorld) {
            return serverWorld.getServer().getPlayerManager().getPlayer(ownerUuid);
        }
        return null;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    @Override
    public boolean canTarget(LivingEntity target) {
        if (target instanceof PlayerEntity player && ownerUuid != null) {
            return !player.getUuid().equals(ownerUuid);
        }
        return super.canTarget(target);
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (ownerUuid != null) nbt.putUuid("OwnerUUID", ownerUuid);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.containsUuid("OwnerUUID")) ownerUuid = nbt.getUuid("OwnerUUID");
    }
}
