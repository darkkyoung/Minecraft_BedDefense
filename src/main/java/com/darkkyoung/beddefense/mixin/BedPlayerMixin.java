package com.darkkyoung.beddefense.mixin;

import com.darkkyoung.beddefense.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
abstract class BedPlayerMixin {
    @Inject(method="setRespawnPosition",at=@At("HEAD"),cancellable=true)
    private void beddefense$respawn(ServerPlayer.RespawnConfig config,boolean message,CallbackInfo ci){
        if(!SpawnBedPrototype.allowedRespawn((ServerPlayer)(Object)this,config))ci.cancel();
    }
    @Inject(method="startSleeping",at=@At("HEAD"),cancellable=true)
    private void beddefense$sleep(BlockPos pos,CallbackInfoReturnable<Boolean> cir){
        ServerPlayer self=(ServerPlayer)(Object)this;ServerLevel level=(ServerLevel)self.level();ServerLevel overworld=level.getServer().overworld();
        if(!ChallengeData.get(overworld).running())return;
        var state=level.getBlockState(pos);
        if(RaidManager.isActive(level.getServer())||!level.dimension().equals(Level.OVERWORLD)
            ||!(state.getBlock() instanceof BedBlock)||!SpawnBedPrototype.footPosition(pos,state).equals(SpawnBedPrototype.registeredPosition(self,overworld)))cir.setReturnValue(false);
    }
}
