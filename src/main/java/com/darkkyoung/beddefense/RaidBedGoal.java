package com.darkkyoung.beddefense;

import java.util.EnumSet;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

/** Reserves movement while the server-selected target is a bed; vanilla melee remains active for player targets. */
public final class RaidBedGoal extends Goal {
    private final Mob mob;
    public RaidBedGoal(Mob mob){this.mob=mob;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
    @Override public boolean canUse(){return RaidManager.bedTarget(mob.getUUID())!=null;}
    @Override public void tick(){var pos=RaidManager.bedTarget(mob.getUUID());if(pos!=null)mob.getLookControl().setLookAt(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5);}
    @Override public void stop(){mob.getNavigation().stop();}
}
