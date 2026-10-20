package com.darkkyoung.beddefense;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

import com.mojang.math.Transformation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * True vanilla Glowing outline for both halves of the one registered bed.
 * A visually enlarged BlockDisplay overlay is non-colliding and never replaces
 * the original bed, so it cannot cause a duplicate vanilla item drop.
 */
public final class SpawnBedGlow {
    private static final String TAG = "beddefense:spawn_bed_outline";
    private static final String FOOT_TAG = "beddefense:outline_foot";
    private static final String HEAD_TAG = "beddefense:outline_head";
    private static final String POSITION_PREFIX = "beddefense:bedpos:";
    private static final int GLOW_COLOR = 0x65F58A;
    private static final Map<UUID, ServerLevel> PENDING_LOADS = new HashMap<>();

    private SpawnBedGlow() {}

    private static String positionTag(BlockPos foot) {
        return POSITION_PREFIX + foot.asLong();
    }

    public static void ensure(ServerLevel world, BlockPos foot) {
        if (foot == null || !world.isLoaded(foot)) return;
        BlockState state = world.getBlockState(foot);
        if (!(state.getBlock() instanceof BedBlock) || state.getValue(BedBlock.PART) != BedPart.FOOT) {
            clear(world,foot);
            return;
        }
        BlockPos head = foot.relative(state.getValue(BedBlock.FACING));
        if (!world.isLoaded(head) || !(world.getBlockState(head).getBlock() instanceof BedBlock)) return;
        ensureHalf(world,foot,foot,state,FOOT_TAG);
        ensureHalf(world,foot,head,world.getBlockState(head),HEAD_TAG);
    }

    private static void ensureHalf(ServerLevel world,BlockPos foot,BlockPos part,
                                   BlockState state,String halfTag) {
        var old = markers(world,foot,part,halfTag);
        if(!old.isEmpty()) {
            for(int i=1;i<old.size();i++)old.get(i).discard();
            var first=old.getFirst();
            if(first.isCurrentlyGlowing()) return;
            first.setGlowingTag(true);
            first.setGlowColorOverride(GLOW_COLOR);
            return;
        }
        Display.BlockDisplay display=new Display.BlockDisplay(EntityType.BLOCK_DISPLAY,world);
        display.addTag(TAG);
        display.addTag(positionTag(foot));
        display.addTag(halfTag);
        display.setPos(part.getX(),part.getY(),part.getZ());
        display.setBlockState(state);
        // Keep the overlay only barely larger than the bed, to avoid Z-fighting
        // while allowing Minecraft's green entity outline to show through walls.
        display.setTransformation(new Transformation(
                new Vector3f(-0.008f,-0.004f,-0.008f),
                new Quaternionf(),new Vector3f(1.016f,1.016f,1.016f),
                new Quaternionf()));
        display.setWidth(1.1f);
        display.setHeight(0.9f);
        display.setShadowStrength(0);
        display.setSilent(true);
        display.setNoGravity(true);
        display.setGlowingTag(true);
        display.setGlowColorOverride(GLOW_COLOR);
        world.addFreshEntity(display);
    }

    private static List<Display.BlockDisplay> markers(ServerLevel level,BlockPos foot,
                                                       BlockPos part,String half) {
        return level.getEntitiesOfClass(Display.BlockDisplay.class,
                new AABB(part).inflate(0.2),
                entity -> entity.getTags().contains(TAG) &&
                        entity.getTags().contains(positionTag(foot)) &&
                        entity.getTags().contains(half));
    }

    public static void clear(ServerLevel world,BlockPos foot) {
        if(foot==null || !world.isLoaded(foot)) return;
        String encoded=positionTag(foot);
        for(Display.BlockDisplay display:world.getEntitiesOfClass(Display.BlockDisplay.class,
                new AABB(foot).inflate(2.0),e->e.getTags().contains(TAG)&&e.getTags().contains(encoded)))
            display.discard();
    }

    public static void clearAll(ServerLevel world) {
        for(BlockPos pos:SpawnBedData.get(world).positions().values())
            clear(world,pos);
    }

    /**
     * On chunk reload, discard old markers for a retired run or removed bed.
     * Entity load events can be dispatched during entity-list iteration; defer
     * mutations until the next END_SERVER_TICK.
     */
    public static void onLoad(Entity entity,ServerLevel level) {
        if(entity instanceof Display.BlockDisplay &&
                entity.getTags().contains(TAG) && level.dimension().equals(Level.OVERWORLD))
            PENDING_LOADS.put(entity.getUUID(),level);
    }

    public static void finishTick(MinecraftServer server) {
        if(PENDING_LOADS.isEmpty())return;
        Map<UUID,ServerLevel> queued=new HashMap<>(PENDING_LOADS);
        PENDING_LOADS.clear();
        for(var entry:queued.entrySet()) {
            var world=entry.getValue();
            Entity e=world.getEntity(entry.getKey());
            if(!(e instanceof Display.BlockDisplay display))continue;
            BlockPos foot=readFoot(display);
            if(foot==null || !validOwner(world,foot)) display.discard();
        }
    }

    private static BlockPos readFoot(Display.BlockDisplay display) {
        for(String tag:display.getTags()){
            if(tag.startsWith(POSITION_PREFIX)){
                try{return BlockPos.of(Long.parseLong(tag.substring(POSITION_PREFIX.length())));}
                catch(NumberFormatException ignored){return null;}
            }
        }
        return null;
    }

    private static boolean validOwner(ServerLevel world,BlockPos foot) {
        if(!world.isLoaded(foot) ||
                !(world.getBlockState(foot).getBlock() instanceof BedBlock))return false;
        for(var entry:SpawnBedData.get(world).positions().entrySet()){
            if(entry.getValue().equals(foot) &&
                    BedVitalData.get(world).state(entry.getKey())==1)return true;
        }
        return false;
    }
}
