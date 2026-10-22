package com.darkkyoung.beddefense;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * One indestructible beacon-shaped block. Not a vanilla BeaconBlock: no pyramid
 * checks, powers, inventory/upgrade UI, item drops or normal beacon gameplay.
 */
public final class RecoveryBeaconBlock extends BaseEntityBlock {
    public static final MapCodec<RecoveryBeaconBlock> CODEC=simpleCodec(RecoveryBeaconBlock::new);
    public RecoveryBeaconBlock(BlockBehaviour.Properties properties){super(properties);}
    @Override protected MapCodec<? extends BaseEntityBlock> codec(){return CODEC;}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){
        return new RecoveryBeaconBlockEntity(pos,state);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level world,BlockPos pos,Player player,
                                                         net.minecraft.world.phys.BlockHitResult hit){
        if(!(world instanceof ServerLevel level)||!(player instanceof ServerPlayer serverPlayer))
            return InteractionResult.SUCCESS;
        return RecoveryBeaconManager.open(serverPlayer,level,pos)?InteractionResult.SUCCESS:InteractionResult.FAIL;
    }
    @Override protected InteractionResult useItemOn(net.minecraft.world.item.ItemStack stack,BlockState state,
            Level world,BlockPos pos,Player player,net.minecraft.world.InteractionHand hand,
            net.minecraft.world.phys.BlockHitResult hit){
        return useWithoutItem(state,world,pos,player,hit);
    }
}
