package net.stones.entity.ai;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.player.Player;

public class StonesProtectOwnerGoal extends TargetGoal {
    private final Player owner;

    public StonesProtectOwnerGoal(Mob mob, Player owner) {
        super(mob, false);
        this.owner = owner;
    }

    @Override
    public boolean canUse() {
        if (owner == null || !owner.isAlive()) return false;

        // Target 1: Was der Spieler schlägt
        LivingEntity target = owner.getLastHurtMob();
        // Target 2: Was den Spieler angreift
        if (target == null || !target.isAlive() || target == mob) {
            target = owner.getLastHurtByMob();
        }

        if (target != null && target.isAlive() && target != mob) {
            this.targetMob = target;
            return true;
        }
        return false;
    }

    @Override
    public void start() {
        this.mob.setTarget(this.targetMob);
        super.start();
    }
}