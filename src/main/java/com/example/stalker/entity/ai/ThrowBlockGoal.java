package com.example.stalker.entity.ai;

import com.example.stalker.entity.StalkerEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.util.EnumSet;

public class ThrowBlockGoal extends Goal {
    private final StalkerEntity stalker;
    private PlayerEntity target;
    private int cooldown;

    public ThrowBlockGoal(StalkerEntity stalker) {
        this.stalker = stalker;
        this.setControls(EnumSet.of(Control.MOVE));
    }

    @Override
    public boolean canStart() {
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        LivingEntity targetEntity = this.stalker.getTarget();
        if (targetEntity instanceof PlayerEntity player) {
            this.target = player;
            double dist = this.stalker.distanceTo(this.target);
            return dist > 4.0 && dist < 20.0
                    && !StalkGoal.isPlayerLookingAtStalker(this.target, this.stalker);
        }
        return false;
    }

    @Override
    public boolean shouldContinue() {
        return false;
    }

    @Override
    public void start() {
        if (target == null || stalker.getWorld().isClient()) return;

        BlockPos foundBlock = findNearbyBlock();
        if (foundBlock != null) {
            stalker.getWorld().breakBlock(foundBlock, false);

            float damage = stalker.isEnraged() ? 10.0f : 6.0f;
            target.damage(stalker.getWorld().getDamageSources().mobAttack(stalker), damage);

            Vec3d knockback = target.getPos().subtract(stalker.getPos()).normalize();
            target.addVelocity(knockback.x * 1.5, 0.4, knockback.z * 1.5);
            target.velocityModified = true;

            stalker.playSound(SoundEvents.ENTITY_IRON_GOLEM_ATTACK, 1.5F, 0.5F);
            cooldown = stalker.isEnraged() ? 40 : 80;
        }
    }

    private BlockPos findNearbyBlock() {
        BlockPos stalkerPos = stalker.getBlockPos();
        for (int x = -3; x <= 3; x++) {
            for (int y = -1; y <= 2; y++) {
                for (int z = -3; z <= 3; z++) {
                    BlockPos pos = stalkerPos.add(x, y, z);
                    BlockState state = stalker.getWorld().getBlockState(pos);
                    if (!state.isAir()
                            && !state.isOf(Blocks.BEDROCK)
                            && !state.isOf(Blocks.OBSIDIAN)
                            && !state.isOf(Blocks.END_PORTAL_FRAME)
                            && !state.isOf(Blocks.BARRIER)) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }
}
