package com.example.stalker.entity.ai;

import com.example.stalker.entity.StalkerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.sound.SoundEvents;
import java.util.EnumSet;

public class TeleportAttackGoal extends Goal {
    private final StalkerEntity stalker;
    private PlayerEntity target;
    private int cooldown;

    public TeleportAttackGoal(StalkerEntity stalker) {
        this.stalker = stalker;
        this.setControls(EnumSet.of(Control.MOVE));
    }

    @Override
    public boolean canStart() {
        LivingEntity targetEntity = this.stalker.getTarget();
        if (targetEntity instanceof PlayerEntity) {
             this.target = (PlayerEntity) targetEntity;
             return this.stalker.distanceTo(this.target) > 3.0;
        }
        return false;
    }

    @Override
    public void tick() {
        if (target == null) return;
        if (cooldown > 0) cooldown--;

        if (cooldown <= 0 && !StalkGoal.isPlayerLookingAtStalker(target, stalker)) {
             teleportBehind(target);
             cooldown = 60; 
        }
    }

    private void teleportBehind(PlayerEntity target) {
        Vec3d lookVec = target.getRotationVec(1.0F).normalize();
        Vec3d targetPos = target.getPos();
        // Position behind: targetPos - lookVec * 4
        Vec3d teleportPos = targetPos.subtract(lookVec.multiply(4.0));
        
        // Try to teleport
        if (stalker.teleport(teleportPos.x, target.getY(), teleportPos.z, true)) {
             stalker.playSound(SoundEvents.ENTITY_ENDERMAN_TELEPORT, 1.0F, 1.0F);
             stalker.getNavigation().startMovingTo(target, 1.5);
        }
    }
}
