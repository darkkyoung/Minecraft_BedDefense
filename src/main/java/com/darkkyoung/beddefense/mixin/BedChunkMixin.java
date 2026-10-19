package com.darkkyoung.beddefense.mixin;

import com.darkkyoung.beddefense.SpawnBedPrototype;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
abstract class BedChunkMixin {
    @Inject(method="setBlockState",at=@At("RETURN"))
    private void beddefense$changed(BlockPos pos,BlockState state,int flags,CallbackInfoReturnable<BlockState> cir){
        LevelChunk self=(LevelChunk)(Object)this;
        if(self.getLevel() instanceof ServerLevel level&&cir.getReturnValue()!=null)
            SpawnBedPrototype.blockChanged(level,pos,cir.getReturnValue(),state);
    }
}
