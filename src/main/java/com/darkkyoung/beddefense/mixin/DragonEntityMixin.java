package com.darkkyoung.beddefense.mixin;
import com.darkkyoung.beddefense.ChallengeLifecycle;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Dragon.kill bypasses LivingEntity.die and can precede entity-tracker registration. */
@Mixin(EnderDragon.class)
abstract class DragonEntityMixin {
    @Inject(method="kill",at=@At("TAIL"))
    private void beddefense$killed(ServerLevel level,CallbackInfo ci){ChallengeLifecycle.dragonKilled(level.getServer());}
}
