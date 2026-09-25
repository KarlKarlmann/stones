package net.stones.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import java.util.EnumSet;

public class StonesFollowOwnerGoal extends Goal {
    private final Mob mob;
    private final Player owner;
    private final double speed;

    public StonesFollowOwnerGoal(Mob mob, Player owner, double speed) {
        this.mob = mob;
        this.owner = owner;
        this.speed = speed;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (owner == null || !owner.isAlive() || owner.isSpectator()) return false;
        return mob.distanceToSqr(owner) > 12.0D; // Distanz > ~3.5 Blöcke
    }

    @Override
    public void tick() {
        mob.getLookControl().setLookAt(owner, 10.0F, (float) mob.getMaxHeadXRot());
        double distSqr = mob.distanceToSqr(owner);
        
        if (distSqr >= 144.0D) { // Teleport ab 12 Blöcken Distanz (wie Vanilla)
            teleportToOwner();
        } else {
            mob.getNavigation().moveTo(owner, speed);
        }
    }

    private void teleportToOwner() {
        BlockPos pos = owner.blockPosition();
        for (int i = 0; i < 10; i++) {
            int x = pos.getX() + mob.getRandom().nextInt(5) - 2;
            int y = pos.getY() + mob.getRandom().nextInt(3) - 1;
            int z = pos.getZ() + mob.getRandom().nextInt(5) - 2;
            if (mob.level().getBlockState(new BlockPos(x, y, z)).isAir()) {
                mob.moveTo(x + 0.5, y, z + 0.5, mob.getYRot(), mob.getXRot());
                mob.getNavigation().stop();
                return;
            }
        }
    }
}