package com.darkkyoung.beddefense.mixin;

import com.darkkyoung.beddefense.RaidManager;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class RaidMobMixin {
    @Inject(method="setTarget",at=@At("HEAD"),cancellable=true)
    private void beddefense$bedPriority(LivingEntity target,CallbackInfo ci){
        if(target!=null&&RaidManager.bedTarget(((Mob)(Object)this).getUUID())!=null)ci.cancel();
    }
}
