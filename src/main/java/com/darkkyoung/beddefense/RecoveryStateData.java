package com.darkkyoung.beddefense;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.core.Direction;

/** Durable per-owner recovery usage and original bed orientation, NEVER item-NBT authority. */
public final class RecoveryStateData extends SavedData {
    public static final int MAX_RECOVERIES = 2;
    public static final Codec<RecoveryStateData> CODEC=RecordCodecBuilder.create(i->i.group(
            Codec.unboundedMap(Codec.STRING,Codec.INT).optionalFieldOf("used",Map.of()).forGetter(d->Map.copyOf(d.used)),
            Codec.unboundedMap(Codec.STRING,Codec.STRING).optionalFieldOf("facing",Map.of()).forGetter(d->Map.copyOf(d.facing))
    ).apply(i,RecoveryStateData::new));
    static final SavedDataType<RecoveryStateData> TYPE=new SavedDataType<>(
            Identifier.fromNamespaceAndPath("beddefense","bed_recoveries"),RecoveryStateData::new,CODEC,null);
    private final Map<String,Integer> used;
    private final Map<String,String> facing;

    public RecoveryStateData(){this(Map.of(),Map.of());}
    private RecoveryStateData(Map<String,Integer> used,Map<String,String> facing){
        this.used=new HashMap<>(used);this.facing=new HashMap<>(facing);
    }
    public static RecoveryStateData get(ServerLevel overworld){return overworld.getDataStorage().computeIfAbsent(TYPE);}
    public int used(UUID owner){return Math.max(0,Math.min(MAX_RECOVERIES,used.getOrDefault(owner.toString(),0)));}
    public boolean canRecover(UUID owner){return used(owner)<MAX_RECOVERIES;}
    public int nextCostStage(UUID owner){return used(owner)+1;}
    public Direction facing(UUID owner){
        try {
            Direction d=Direction.valueOf(facing.getOrDefault(owner.toString(),"NORTH"));
            return d.getAxis().isHorizontal()?d:Direction.NORTH;
        }catch(IllegalArgumentException e){return Direction.NORTH;}
    }
    public void rememberFacing(UUID owner,Direction direction){
        if(!direction.getAxis().isHorizontal())return;
        facing.put(owner.toString(),direction.name());setDirty();
    }
    public boolean consumeRecovery(UUID owner){
        if(!canRecover(owner))return false;
        used.put(owner.toString(),used(owner)+1);setDirty();return true;
    }
    public void resetForNewRun(){used.clear();facing.clear();setDirty();}
}
