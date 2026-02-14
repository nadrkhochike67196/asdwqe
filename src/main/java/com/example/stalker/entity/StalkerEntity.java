package com.example.stalker.entity;

import com.example.stalker.entity.ai.ScreamGoal;
import com.example.stalker.entity.ai.StalkGoal;
import com.example.stalker.entity.ai.TeleportAttackGoal;
import com.example.stalker.entity.ai.ThrowBlockGoal;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.World;

public class StalkerEntity extends HostileEntity {
    private boolean hasEnteredRage = false;

    public StalkerEntity(EntityType<? extends HostileEntity> entityType, World world) {
        super(entityType, world);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(1, new StalkGoal(this));
        this.goalSelector.add(2, new ScreamGoal(this));
        this.goalSelector.add(3, new TeleportAttackGoal(this));
        this.goalSelector.add(4, new ThrowBlockGoal(this));
        this.goalSelector.add(5, new MeleeAttackGoal(this, 1.2, false));
        this.goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 64.0F));
        this.goalSelector.add(7, new WanderAroundFarGoal(this, 0.8));

        this.targetSelector.add(1, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
        this.targetSelector.add(2, new RevengeGoal(this));
    }

    public static DefaultAttributeContainer.Builder createStalkerAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 80.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.38)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 12.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 64.0)
                .add(EntityAttributes.GENERIC_ARMOR, 6.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.5);
    }

    @Override
    public void tickMovement() {
        super.tickMovement();

        if (!this.getWorld().isClient()) {
            // Darkness aura
            var nearbyPlayers = this.getWorld().getEntitiesByClass(
                    PlayerEntity.class,
                    this.getBoundingBox().expand(16.0),
                    player -> true
            );
            for (PlayerEntity player : nearbyPlayers) {
                player.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.DARKNESS, 60, 0, false, false, true
                ));
            }

            // Rage mode
            if (isEnraged() && !hasEnteredRage) {
                hasEnteredRage = true;
                this.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 99999, 1, false, false));
                this.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 99999, 1, false, false));
                this.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 99999, 0, false, false));
                this.playSound(SoundEvents.ENTITY_WARDEN_ROAR, 2.0F, 0.3F);
            }

            // Rage particles
            if (isEnraged() && this.getWorld() instanceof ServerWorld serverWorld) {
                serverWorld.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        this.getX(), this.getY() + 1.0, this.getZ(),
                        3, 0.3, 0.5, 0.3, 0.02);
            }
        }
    }

    public boolean isEnraged() {
        return this.getHealth() < this.getMaxHealth() * 0.3f;
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        // Dodge: 30% chance (50% when enraged)
        double dodgeChance = isEnraged() ? 0.5 : 0.3;
        if (!this.getWorld().isClient() && this.random.nextDouble() < dodgeChance) {
            for (int i = 0; i < 16; i++) {
                double x = this.getX() + (this.random.nextDouble() - 0.5) * 12.0;
                double y = this.getY() + (this.random.nextDouble() - 0.5) * 4.0;
                double z = this.getZ() + (this.random.nextDouble() - 0.5) * 12.0;
                if (this.teleport(x, y, z, true)) {
                    this.playSound(SoundEvents.ENTITY_ENDERMAN_TELEPORT, 1.0F, 1.0F);
                    return false;
                }
            }
        }

        // Counter-attack when enraged
        if (isEnraged() && source.getAttacker() instanceof LivingEntity attacker) {
            attacker.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 60, 1));
        }

        return super.damage(source, amount);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENTITY_WARDEN_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_WARDEN_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_WARDEN_DEATH;
    }
}
