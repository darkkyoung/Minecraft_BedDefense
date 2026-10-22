package com.darkkyoung.beddefense;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** One custom block that borrows vanilla beacon TEXTURE and beam renderer, not powers. */
public final class RecoveryBeaconRegistry {
    public static final Identifier BLOCK_ID=Identifier.fromNamespaceAndPath("beddefense","recovery_beacon");
    public static final Identifier ENTITY_ID=Identifier.fromNamespaceAndPath("beddefense","recovery_beacon");
    public static Block BLOCK;
    public static BlockEntityType<RecoveryBeaconBlockEntity> ENTITY_TYPE;
    private RecoveryBeaconRegistry(){}

    public static void init(){
        BLOCK=Registry.register(BuiltInRegistries.BLOCK,BLOCK_ID,new RecoveryBeaconBlock(
                BlockBehaviour.Properties.ofFullCopy(Blocks.BEACON)
                        .setId(ResourceKey.create(Registries.BLOCK,BLOCK_ID))
                        .strength(-1.0F,3600000F).pushReaction(net.minecraft.world.level.material.PushReaction.IMMOVEABLE).noLootTable()));
        ENTITY_TYPE=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,ENTITY_ID,
                FabricBlockEntityTypeBuilder.create(RecoveryBeaconBlockEntity::new,BLOCK).build());
    }
    public static boolean isRecoveryBlock(net.minecraft.world.level.Level world,BlockPos pos){
        return world.getBlockState(pos).is(BLOCK);
    }
}
