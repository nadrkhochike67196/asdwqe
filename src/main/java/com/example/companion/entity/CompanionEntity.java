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
    private final TaskStateMachine taskMachine = new TaskStateMachine();
    private final SimpleInventory inventory = new SimpleInventory(27);
    private int aiTickCounter;
    private int buildTickCounter;
    private String currentAction = "idle";
    private BuildingSystem.BuildingTask buildingTask;

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

        // ── Task state machine — runs EVERY tick for persistent actions ──
        taskMachine.tick(this);

        // Continue building task (3 blocks per second)
        buildTickCounter++;
        if (buildTickCounter >= 10 && buildingTask != null && !buildingTask.completed) {
            buildTickCounter = 0;
            boolean done = BuildingSystem.executeStep(this, buildingTask, 3);
            if (done) {
                broadcastMessage(PersonalitySystem.formatMessage(
                        "ГОТОВО! Построил " + buildingTask.blueprint.name(),
                        PersonalitySystem.ResponseStyle.EXCITED));
                buildingTask = null;
            }
        }

        // Spontaneous reactions
        checkAndReact();

        // AI decision every 3 seconds (was 5s — faster = more responsive)
        aiTickCounter++;
        if (aiTickCounter >= 60) {
            aiTickCounter = 0;
            queryAI();
        }
    }

    /**
     * Check for events and react spontaneously
     */
    private void checkAndReact() {
        // Only react every 2 seconds
        if (aiTickCounter % 40 != 0) return;

        PlayerEntity owner = getOwnerPlayer();
        if (owner == null) return;

        // Player low HP
        if (owner.getHealth() <= 6 && owner.getHealth() > 0) {
            broadcastMessage(PersonalitySystem.reactToEvent(PersonalitySystem.GameEventType.PLAYER_LOW_HP));
        }

        // Check for creepers
        var creepers = this.getWorld().getEntitiesByClass(
                net.minecraft.entity.mob.CreeperEntity.class,
                this.getBoundingBox().expand(10.0), e -> true);
        if (!creepers.isEmpty()) {
            broadcastMessage(PersonalitySystem.reactToEvent(PersonalitySystem.GameEventType.CREEPER_NEARBY));
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

        // Check for direct commands first
        PlayerCommandSystem.CommandType cmd = PlayerCommandSystem.parseCommand(message);
        switch (cmd) {
            case TELEPORT -> {
                PlayerCommandSystem.teleportToPlayer(this, player);
                broadcastMessage(PersonalitySystem.formatMessage(
                        "Телепортнулся! Что надо?",
                        PersonalitySystem.ResponseStyle.CASUAL));
                return;
            }
            case STAY -> {
                this.getNavigation().stop();
                this.taskMachine.cancel();  // Cancel any active task
                broadcastMessage(PersonalitySystem.formatMessage(
                        "Ок, стою тут",
                        PersonalitySystem.ResponseStyle.CASUAL));
                return;
            }
            case BUILD -> {
                // Extract building type from message and start build
                String blueprintKey = BuildingSystem.findBlueprint(message);
                if (blueprintKey != null) {
                    ActionExecutor.executeBuild(this, blueprintKey);
                } else {
                    broadcastMessage(PersonalitySystem.formatMessage(
                            "Хз что построить. Могу: " + String.join(", ", BuildingSystem.getAvailableBlueprints()),
                            PersonalitySystem.ResponseStyle.ANNOYED));
                }
                return;
            }
            default -> {
                // CHAT or other — pass to AI
            }
        }

        String context = WorldPerceptionSystem.buildContext(this);
        aiBrain.chat(player.getName().getString(), message, context).thenAccept(action -> {
            if (this.getWorld() instanceof ServerWorld sw) {
                sw.getServer().execute(() -> processAction(action));
            }
        });
    }

    private void processAction(AIBrain.AIAction action) {
        this.currentAction = action.action();

        // Apply personality to the message
        String message = action.message();
        if (message != null && !message.isEmpty()) {
            boolean hasHostiles = !this.getWorld().getEntitiesByClass(
                    net.minecraft.entity.mob.HostileEntity.class,
                    this.getBoundingBox().expand(16.0), e -> true).isEmpty();

            PersonalitySystem.ResponseStyle style = PersonalitySystem.determineStyle(
                    action.action(), hasHostiles,
                    (int) this.getHealth(), (int) this.getMaxHealth());

            // Apply personality formatting
            message = PersonalitySystem.formatMessage(message, style);
            broadcastMessage(message);
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

    private void broadcastMessage(String msg) {
        if (msg == null || msg.isEmpty()) return;
        if (this.getWorld() instanceof ServerWorld sw) {
            sw.getServer().getPlayerManager().broadcast(
                    Text.literal("\u00A7b[Buddy] \u00A7f" + msg), false);
            // Also send as TTS audio
            TTSSystem.speakToAll(msg, sw);
        }
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
    public TaskStateMachine getTaskMachine() { return taskMachine; }
    public UUID getOwnerUuid() { return ownerUuid; }
    public BuildingSystem.BuildingTask getBuildingTask() { return buildingTask; }
    public void setBuildingTask(BuildingSystem.BuildingTask task) { this.buildingTask = task; }

    /** True when the companion has an active task (mining, moving, building). FollowOwnerGoal yields to this. */
    public boolean isBusy() {
        return taskMachine.isBusy() || (buildingTask != null && !buildingTask.completed);
    }

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
