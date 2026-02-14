package com.example.companion.entity.ai;

import com.example.companion.entity.CompanionEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.player.PlayerEntity;

import java.util.EnumSet;

public class FollowOwnerGoal extends Goal {
    private final CompanionEntity companion;
    private PlayerEntity owner;
    private final double speed;
    private final double maxDist;
    private final double minDist;
    private int updateTimer;

    public FollowOwnerGoal(CompanionEntity companion, double speed, double maxDist, double minDist) {
        this.companion = companion;
        this.speed = speed;
        this.maxDist = maxDist;
        this.minDist = minDist;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        PlayerEntity owner = this.companion.getOwnerPlayer();
        if (owner == null) return false;
        if (this.companion.distanceTo(owner) < this.minDist) return false;
        this.owner = owner;
        return true;
    }

    @Override
    public boolean shouldContinue() {
        if (this.owner == null || !this.owner.isAlive()) return false;
        return this.companion.distanceTo(this.owner) > this.minDist;
    }

    @Override
    public void start() {
        this.updateTimer = 0;
    }

    @Override
    public void tick() {
        this.companion.getLookControl().lookAt(this.owner, 10.0F, this.companion.getMaxLookPitchChange());
        if (--this.updateTimer <= 0) {
            this.updateTimer = 10;
            if (this.companion.distanceTo(this.owner) > 30.0) {
                this.companion.requestTeleport(this.owner.getX(), this.owner.getY(), this.owner.getZ());
            } else {
                this.companion.getNavigation().startMovingTo(this.owner, this.speed);
            }
        }
    }

    @Override
    public void stop() {
        this.owner = null;
        this.companion.getNavigation().stop();
    }
}
