package com.example.stalker.entity.ai;

import com.example.stalker.entity.StalkerEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import java.util.EnumSet;

public class StalkGoal extends Goal {
    private final StalkerEntity stalker;
    private PlayerEntity target;

    public StalkGoal(StalkerEntity stalker) {
        this.stalker = stalker;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        this.target = this.stalker.getWorld().getClosestPlayer(this.stalker, 64.0);
        return this.target != null;
    }

    @Override
    public void tick() {
        if (this.target == null) return;
        
        this.stalker.getLookControl().lookAt(this.target, 30.0F, 30.0F);

        if (isPlayerLookingAtStalker(this.target, this.stalker)) {
            this.stalker.getNavigation().stop();
        } else {
            this.stalker.getNavigation().startMovingTo(this.target, 1.2);
        }
    }

    public static boolean isPlayerLookingAtStalker(PlayerEntity player, StalkerEntity stalker) {
        Vec3d vec3d = player.getRotationVec(1.0F).normalize();
        Vec3d vec3d2 = new Vec3d(stalker.getX() - player.getX(), stalker.getEyeY() - player.getEyeY(), stalker.getZ() - player.getZ());
        double d = vec3d2.length();
        vec3d2 = vec3d2.normalize();
        double e = vec3d.dotProduct(vec3d2);
        return e > 1.0 - 0.3 / (d == 0 ? 1 : d) && player.canSee(stalker);
    }
}
