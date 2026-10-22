package com.darkkyoung.beddefense.mixin;

import com.darkkyoung.beddefense.RaidManager;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Conversion creates a different entity/UUID, losing the original raid ledger identity.
 * Only tracked Bed Defense zombies retain their zombie form under water.
 * Untracked vanilla zombies still convert normally.
 */
@Mixin(Zombie.class)
abstract class RaidZombieWaterMixin {
    @Inject(method="convertsInWater()Z",at=@At("HEAD"),cancellable=true)
    private void beddefense$keepRaiderUuidInWater(CallbackInfoReturnable<Boolean> returnValue) {
        if (RaidManager.isTrackedRaider((Zombie)(Object)this)) returnValue.setReturnValue(false);
    }
}
