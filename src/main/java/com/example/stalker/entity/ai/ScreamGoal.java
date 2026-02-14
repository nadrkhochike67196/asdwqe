package com.example.stalker.entity.ai;

import com.example.stalker.entity.StalkerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvents;
import java.util.EnumSet;

public class ScreamGoal extends Goal {
    private final StalkerEntity stalker;
    private PlayerEntity target;
    private int cooldown;

    public ScreamGoal(StalkerEntity stalker) {
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
            return this.stalker.distanceTo(this.target) < 8.0
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

        target.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 100, 0));
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 1));
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 60, 0));

        if (stalker.isEnraged()) {
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 80, 1));
        }

        stalker.playSound(SoundEvents.ENTITY_WARDEN_ROAR, 2.0F, 0.5F);
        cooldown = 200;
    }
}
