package com.darkkyoung.beddefense;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Bounded siege helper for tagged raid entities only.
 * Mines the first blocking voxel after repeated failed path progress, displays
 * vanilla cracks, and fills short walkable gaps with cobblestone (1–2s cooldown).
 * It never damages Spawn Bed blocks directly: those use the BedVital HP system.
 * No expensive full-world scan; called by RaidManager's 1Hz raider pass.
 */
public final class SiegeController {
    private static final int MAX_MINERS = 12;
    private static final int MAX_PLACED_BY_ONE_MOB = 24;
    private static final Map<UUID, Job> JOBS = new HashMap<>();
    private static final double[] DIRT = {5,4.5,4,3.5,3,2.5,2,1.8,1.5,1.3};
    private static final double[] WOOD = {12,11,10,8.5,7,6,5,4.2,3.6,3};
    private static final double[] COBBLE = {30,27,24,21,18,15,12,10.5,9,7.5};
    private static final double[] OBSIDIAN = {120,108,96,84,72,60,48,42,36,30};
    private SiegeController() {}

    private static final class Job {
        BlockPos lastFeet;
        int stuckSeconds;
        int placedBlocks;
        long nextPlacementTick;
        BlockPos breaking;
        long breakStart;
        int lastStage=-1;
        ServerLevel level;
        int breakerId;
        long lastSeen;

        Job(Mob mob,ServerLevel world){
            lastFeet=mob.blockPosition();
            level=world;
            breakerId=-1000000-Math.floorMod(mob.getUUID().hashCode(),100000000);
            lastSeen=world.getGameTime();
        }
    }

    public static void attempt(Mob mob,ServerLevel level,BlockPos target,int raidDay) {
        if(target==null || mob.isRemoved() || mob.isDeadOrDying()) {
            clear(mob.getUUID());
            return;
        }
        Job job=JOBS.computeIfAbsent(mob.getUUID(),id->new Job(mob,level));
        if(job.level!=level) {clearBreaking(job);job.level=level;}
        job.lastSeen=level.getGameTime();
        BlockPos feet=mob.blockPosition();
        if(job.lastFeet.distSqr(feet)<0.36)job.stuckSeconds++;
        else {job.stuckSeconds=0;clearBreaking(job);}
        job.lastFeet=feet.immutable();
        if(job.stuckSeconds<3)return;

        int dx=Integer.compare(target.getX(),feet.getX());
        int dz=Integer.compare(target.getZ(),feet.getZ());
        if(dx==0 && dz==0) return;
        // Prefer a direct horizontal approach. Do not mine while the entity is
        // successfully moving or when its intended target is already adjacent.
        BlockPos ahead=feet.offset(dx,0,dz);
        if(!level.isLoaded(ahead))return;
        BlockPos blockage=!level.isEmptyBlock(ahead) ? ahead
                : !level.isEmptyBlock(ahead.above()) ? ahead.above() : null;

        if(blockage!=null) {
            if(canMine(level,blockage)){
                mine(mob,job,level,blockage,raidDay);
            } else {
                clearBreaking(job);
            }
            return;
        }

        clearBreaking(job);
        // A single-gap bridge: limited per-mob and paced by a 1–2 second
        // server-tick cooldown. This is not an unlimited tower-building AI.
        BlockPos floor=ahead.below();
        if(job.placedBlocks < MAX_PLACED_BY_ONE_MOB
                && level.isLoaded(floor) && level.isEmptyBlock(floor)
                && level.getGameTime()>=job.nextPlacementTick
                && level.isEmptyBlock(ahead) && level.isEmptyBlock(ahead.above())) {
            level.setBlockAndUpdate(floor,Blocks.COBBLESTONE.defaultBlockState());
            job.placedBlocks++;
            job.nextPlacementTick=level.getGameTime()+20+ThreadLocalRandom.current().nextInt(21);
            // Native block cracks already communicate digging; arm swing pending mapping verification.
        }
    }

    private static boolean canMine(ServerLevel level,BlockPos pos) {
        BlockState block=level.getBlockState(pos);
        float hardness=block.getDestroySpeed(level,pos);
        if(hardness<0 || hardness>50 || block.isAir())return false;
        if(block.hasBlockEntity() || block.getBlock() instanceof BedBlock)return false;
        if(block.is(Blocks.BEDROCK) || block.is(Blocks.END_PORTAL) ||
                block.is(Blocks.NETHER_PORTAL) || block.is(Blocks.END_PORTAL_FRAME) ||
                block.is(Blocks.BARRIER) || block.is(Blocks.REINFORCED_DEEPSLATE)) return false;
        return true;
    }

    private static double durationSeconds(ServerLevel level,BlockPos pos,int day) {
        BlockState state=level.getBlockState(pos);
        int band=Math.max(0,Math.min(9,(day-1)/10));
        double sec;
        if(state.is(Blocks.OBSIDIAN) || state.is(Blocks.CRYING_OBSIDIAN))sec=OBSIDIAN[band];
        else if(state.is(Blocks.DIRT)||state.is(Blocks.GRASS_BLOCK)||state.is(Blocks.SAND))sec=DIRT[band];
        else if(state.is(Blocks.OAK_PLANKS)||state.is(Blocks.SPRUCE_PLANKS)||state.is(Blocks.BIRCH_PLANKS))sec=WOOD[band];
        else if(state.is(Blocks.COBBLESTONE)||state.is(Blocks.STONE))sec=COBBLE[band];
        else sec=Math.max(2,Math.min(OBSIDIAN[band],COBBLE[band]*
                (state.getDestroySpeed(level,pos)/2.0)));
        // User's requested blanket 20% faster siege mining.
        return Math.max(0.5,sec*0.8);
    }

    private static void mine(Mob mob,Job job,ServerLevel level,BlockPos pos,int day) {
        if(job.breaking!=null && !job.breaking.equals(pos))clearBreaking(job);
        if(job.breaking==null){
            // Bound the number of entities doing expensive block work together.
            int active=0;
            for(Job current:JOBS.values())if(current.breaking!=null)active++;
            if(active>=MAX_MINERS)return;
            job.breaking=pos.immutable();
            job.breakStart=level.getGameTime();
            job.lastStage=-1;
        }
        int duration=Math.max(10,(int)Math.ceil(durationSeconds(level,pos,day)*20));
        long age=level.getGameTime()-job.breakStart;
        int stage=Math.min(9,(int)(Math.max(0,age)*10/duration));
        if(stage!=job.lastStage){
            level.destroyBlockProgress(job.breakerId,pos,stage);
            job.lastStage=stage;
            // Native block cracks already communicate digging; arm swing pending mapping verification.
        }
        if(age>=duration){
            level.destroyBlockProgress(job.breakerId,pos,-1);
            if(canMine(level,pos))level.destroyBlock(pos,true);
            job.breaking=null;job.stuckSeconds=0;job.lastStage=-1;
        }
    }

    private static void clearBreaking(Job job){
        if(job.breaking!=null)job.level.destroyBlockProgress(job.breakerId,job.breaking,-1);
        job.breaking=null;
        job.lastStage=-1;
    }
    public static void clear(UUID id){
        Job job=JOBS.remove(id);
        if(job!=null)clearBreaking(job);
    }
    public static void cleanup(long now){
        Iterator<Map.Entry<UUID,Job>> iter=JOBS.entrySet().iterator();
        while(iter.hasNext()){
            Job job=iter.next().getValue();
            if(now-job.lastSeen>1200){
                clearBreaking(job);
                iter.remove();
            }
        }
    }
}
