package com.darkkyoung.beddefense;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;

/** Real server-side entities: outline is visual-only and has no item drops. */
public final class SpawnBedGlowGameTest {
    @GameTest(dimension="minecraft:overworld",maxTicks=120)
    public void outlineTracksBothBedHalvesWithoutItems(GameTestHelper h) {
        ServerLevel world=h.getLevel();
        BlockPos foot=h.absolutePos(new BlockPos(1,2,1));
        BlockPos head=foot.south();
        world.setBlock(foot.below(),Blocks.STONE.defaultBlockState(),2);
        world.setBlock(head.below(),Blocks.STONE.defaultBlockState(),2);
        var block=Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING,Direction.SOUTH);
        world.setBlock(foot,block.setValue(BedBlock.PART,BedPart.FOOT),2);
        world.setBlock(head,block.setValue(BedBlock.PART,BedPart.HEAD),2);

        SpawnBedGlow.ensure(world,foot);
        SpawnBedGlow.ensure(world,foot); // Never spawn more markers when called every 12 ticks.
        var visual=world.getEntitiesOfClass(Display.BlockDisplay.class,
                new AABB(foot).inflate(2.0),
                entity->entity.entityTags().contains("beddefense:spawn_bed_outline"));
        h.assertTrue(visual.size()==2,
                "Both bed halves have exactly one display glow each, not duplicate items: "+visual.size());
        for(var marker:visual){
            h.assertTrue(marker.isCurrentlyGlowing(),"bed display is outlined using real vanilla Glowing");
            h.assertTrue(marker.getGlowColorOverride()==0x65F58A,"outline is green");
            h.assertTrue(marker.isNoGravity(),"visual-only marker does not alter physics");
        }
        SpawnBedGlow.clear(world,foot);
        h.assertTrue(world.getEntitiesOfClass(Display.BlockDisplay.class,
                new AABB(foot).inflate(2.0),
                entity->entity.entityTags().contains("beddefense:spawn_bed_outline")).isEmpty(),
                "No glowing marker remains after manual pickup/destruction");
        h.succeed();
    }
}
