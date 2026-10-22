package com.darkkyoung.beddefense.mixin;

import com.darkkyoung.beddefense.RaidManager;
import net.minecraft.world.entity.monster.zombie.Husk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Husk overrides Zombie.convertsInWater(), so guard its separate conversion
 * to zombie as well. Otherwise a husk could convert twice and disappear
 * from the tracked raid roster.
 */
@Mixin(Husk.class)
abstract class RaidHuskWaterMixin {
    @Inject(method="convertsInWater()Z",at=@At("HEAD"),cancellable=true)
    private void beddefense$keepHuskRaiderUuidInWater(CallbackInfoReturnable<Boolean> returnValue) {
        if (RaidManager.isTrackedRaider((Husk)(Object)this)) returnValue.setReturnValue(false);
    }
}
