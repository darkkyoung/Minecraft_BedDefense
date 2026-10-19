package com.darkkyoung.beddefense;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Persistent wave roster; unloaded mobs stay outstanding until reloaded/settled. */
public final class RaidStateData extends SavedData {
    private static final Codec<RaidStateData> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.LONG.fieldOf("last_raid_day").forGetter(s -> s.lastRaidDay),
        Codec.LONG.fieldOf("active_day").forGetter(s -> s.activeDay),
        Codec.STRING.listOf().fieldOf("mob_ids").forGetter(s -> List.copyOf(s.ids)),
        Codec.BOOL.fieldOf("glow_started").forGetter(s -> s.glowStarted),
        Codec.BOOL.fieldOf("overworld_wave").forGetter(s -> s.overworldWave),
        Codec.INT.fieldOf("total_spawned").forGetter(s -> s.totalSpawned)
    ).apply(i,RaidStateData::new));
    private static final SavedDataType<RaidStateData> TYPE =
        new SavedDataType<>(Identifier.fromNamespaceAndPath("beddefense","raid_state"),RaidStateData::new,CODEC,null);
    private long lastRaidDay;
    private long activeDay;
    private final List<String> ids;
    private boolean glowStarted;
    private boolean overworldWave;
    private int totalSpawned;

    private RaidStateData() {this(0,-1,List.of(),false,false,0);}
    private RaidStateData(long last,long active,List<String> ids,boolean glow,boolean overworld,int total){
        lastRaidDay=last; activeDay=active; this.ids=new ArrayList<>(ids);glowStarted=glow;overworldWave=overworld;totalSpawned=total;
    }
    public static RaidStateData get(ServerLevel overworld){return overworld.getDataStorage().computeIfAbsent(TYPE);}
    public boolean active(){return activeDay>=1;}
    public long activeDay(){return activeDay;}
    public long lastRaidDay(){return lastRaidDay;}
    public boolean glowStarted(){return glowStarted;}
    public boolean overworldWave(){return overworldWave;}
    public int totalSpawned(){return totalSpawned;}
    public void glowStarted(boolean value){glowStarted=value;setDirty();}
    public List<String> ids(){return List.copyOf(ids);}
    public int remaining(){return ids.size();}
    public void launch(long day,List<String> spawned,boolean overworld){
        if(active()||day<=lastRaidDay)throw new IllegalStateException("Duplicate raid launch");
        lastRaidDay=day;activeDay=day;ids.clear();ids.addAll(spawned);glowStarted=false;overworldWave=overworld;totalSpawned=spawned.size();setDirty();
    }
    public void remove(UUID id){if(ids.remove(id.toString()))setDirty();}
    public void finish(){activeDay=-1L;ids.clear();glowStarted=false;overworldWave=false;totalSpawned=0;setDirty();}
}
