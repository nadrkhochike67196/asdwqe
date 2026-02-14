package com.example.companion.entity.ai;

import com.example.companion.entity.CompanionEntity;
import net.minecraft.block.BlockState;
import net.minecraft.entity.ItemEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Persistent task state-machine that runs every tick.
 * Replaces the old fire-and-forget pattern in ActionExecutor.
 * Tasks survive across ticks until completed or cancelled.
 */
public class TaskStateMachine {

    public enum TaskType {
        NONE, MINE, MOVE_TO, PLACE_BLOCK, GIVE_PLAYER, ATTACK
    }

    public enum TaskState {
        IDLE,
        MOVING_TO_TARGET,
        EXECUTING,
        DONE
    }

    // Current task
    private TaskType type = TaskType.NONE;
    private TaskState state = TaskState.IDLE;
    private BlockPos targetPos;
    private String targetItemId;      // for place_block
    private int retries;
    private int ticksSinceNav;        // ticks since last re-navigate
    private int stuckTicks;           // detect if entity isn't moving
    private BlockPos lastPos;         // to detect stuck

    private static final int MAX_RETRIES = 40;       // ~2 seconds of being stuck
    private static final double INTERACT_RANGE = 4.5;
    private static final int NAV_REFRESH_TICKS = 15;  // re-navigate every 15 ticks

    // ── Public API ──────────────────────────────────────────────────────────

    /** Is the state-machine actively doing something? */
    public boolean isBusy() {
        return type != TaskType.NONE && state != TaskState.IDLE && state != TaskState.DONE;
    }

    /** Push a new MINE task. */
    public void setMineTask(BlockPos pos) {
        this.type = TaskType.MINE;
        this.state = TaskState.MOVING_TO_TARGET;
        this.targetPos = pos;
        this.targetItemId = null;
        this.retries = 0;
        this.ticksSinceNav = NAV_REFRESH_TICKS; // navigate immediately
        this.stuckTicks = 0;
        this.lastPos = null;
    }

    /** Push a new MOVE task. */
    public void setMoveTask(BlockPos pos) {
        this.type = TaskType.MOVE_TO;
        this.state = TaskState.MOVING_TO_TARGET;
        this.targetPos = pos;
        this.targetItemId = null;
        this.retries = 0;
        this.ticksSinceNav = NAV_REFRESH_TICKS;
        this.stuckTicks = 0;
        this.lastPos = null;
    }

    /** Push a PLACE_BLOCK task. */
    public void setPlaceTask(BlockPos pos, String itemId) {
        this.type = TaskType.PLACE_BLOCK;
        this.state = TaskState.MOVING_TO_TARGET;
        this.targetPos = pos;
        this.targetItemId = itemId;
        this.retries = 0;
        this.ticksSinceNav = NAV_REFRESH_TICKS;
        this.stuckTicks = 0;
        this.lastPos = null;
    }

    /** Cancel current task and go idle. */
    public void cancel() {
        this.type = TaskType.NONE;
        this.state = TaskState.IDLE;
        this.targetPos = null;
        this.targetItemId = null;
    }

    /** Get current task type. */
    public TaskType getType() { return type; }
    /** Get current state. */
    public TaskState getState() { return state; }

    // ── Tick — called EVERY server tick from CompanionEntity ────────────────

    public void tick(CompanionEntity entity) {
        if (state == TaskState.IDLE || state == TaskState.DONE || type == TaskType.NONE) return;

        World world = entity.getWorld();
        if (world.isClient()) return;

        switch (type) {
            case MINE       -> tickMine(entity);
            case MOVE_TO    -> tickMove(entity);
            case PLACE_BLOCK -> tickPlace(entity);
            default -> { }
        }
    }

    // ── MINE ────────────────────────────────────────────────────────────────

    private void tickMine(CompanionEntity entity) {
        World world = entity.getWorld();
        if (targetPos == null || world.getBlockState(targetPos).isAir()) {
            // Block already broken or gone
            finish(entity);
            return;
        }

        double dist = Math.sqrt(entity.getBlockPos().getSquaredDistance(targetPos));

        if (state == TaskState.MOVING_TO_TARGET) {
            if (dist < INTERACT_RANGE) {
                // Arrived — break the block
                state = TaskState.EXECUTING;
            } else {
                navigateTo(entity, targetPos);
                checkStuck(entity);
            }
        }

        if (state == TaskState.EXECUTING) {
            // Actually break the block
            BlockState bs = world.getBlockState(targetPos);
            if (!bs.isAir()) {
                world.breakBlock(targetPos, true);
                entity.swingHand(Hand.MAIN_HAND);
                entity.playSound(SoundEvents.BLOCK_STONE_BREAK, 1.0F, 1.0F);
            }
            // Wait a couple ticks for items to spawn, then auto-pickup
            finish(entity);
        }
    }

    // ── MOVE ────────────────────────────────────────────────────────────────

    private void tickMove(CompanionEntity entity) {
        if (targetPos == null) { finish(entity); return; }

        double dist = Math.sqrt(entity.getBlockPos().getSquaredDistance(targetPos));
        if (dist < 2.5) {
            finish(entity);
            return;
        }

        navigateTo(entity, targetPos);
        checkStuck(entity);
    }

    // ── PLACE ───────────────────────────────────────────────────────────────

    private void tickPlace(CompanionEntity entity) {
        if (targetPos == null) { finish(entity); return; }

        double dist = Math.sqrt(entity.getBlockPos().getSquaredDistance(targetPos));

        if (dist < INTERACT_RANGE) {
            // Place the block via ActionExecutor's validated place
            ActionExecutor.executePlaceSpecific(entity, targetPos, targetItemId);
            finish(entity);
        } else {
            navigateTo(entity, targetPos);
            checkStuck(entity);
        }
    }

    // ── Navigation helper ───────────────────────────────────────────────────

    private void navigateTo(CompanionEntity entity, BlockPos pos) {
        ticksSinceNav++;
        if (ticksSinceNav >= NAV_REFRESH_TICKS) {
            ticksSinceNav = 0;
            entity.getNavigation().startMovingTo(
                    pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 1.0);
        }
    }

    private void checkStuck(CompanionEntity entity) {
        BlockPos current = entity.getBlockPos();
        if (lastPos != null && lastPos.equals(current)) {
            stuckTicks++;
        } else {
            stuckTicks = 0;
        }
        lastPos = current;

        if (stuckTicks > MAX_RETRIES) {
            // Can't reach target — give up
            cancel();
        }
    }

    private void finish(CompanionEntity entity) {
        this.state = TaskState.DONE;
        this.type = TaskType.NONE;
        entity.getNavigation().stop();
    }
}
