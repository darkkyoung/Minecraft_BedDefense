package com.darkkyoung.beddefense.mixin;

import com.darkkyoung.beddefense.ChallengeLifecycle;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.dimension.end.EnderDragonFight;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EnderDragonFight.class)
abstract class DragonFightMixin {
    @Inject(method="setDragonKilled",at=@At("TAIL"))
    private void beddefense$victory(EnderDragon dragon,CallbackInfo ci){
        if(dragon.isDeadOrDying()&&dragon.level() instanceof ServerLevel level)ChallengeLifecycle.dragonKilled(level.getServer());
    }
}
