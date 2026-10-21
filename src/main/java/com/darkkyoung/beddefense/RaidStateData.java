package com.darkkyoung.beddefense;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Durable per-entity dimension/owner ledger. No loaded-chunk scan determines morning penalties. */
public final class RaidStateData extends SavedData {
    public static final String OVERWORLD="minecraft:overworld";
    public record Raider(String dimension,String owner,int roster,long wave) {
        static final Codec<Raider> CODEC=RecordCodecBuilder.create(i->i.group(
            Codec.STRING.fieldOf("dimension").forGetter(Raider::dimension),Codec.STRING.fieldOf("owner").forGetter(Raider::owner),
            Codec.INT.fieldOf("roster").forGetter(Raider::roster),Codec.LONG.fieldOf("wave").forGetter(Raider::wave)
        ).apply(i,Raider::new));
    }
    public record Pending(String dimension,String owner,int roster,long wave,int next,int count) {
        static final Codec<Pending> CODEC=RecordCodecBuilder.create(i->i.group(
            Codec.STRING.fieldOf("dimension").forGetter(Pending::dimension),Codec.STRING.fieldOf("owner").forGetter(Pending::owner),
            Codec.INT.fieldOf("roster").forGetter(Pending::roster),Codec.LONG.fieldOf("wave").forGetter(Pending::wave),
            Codec.INT.fieldOf("next").forGetter(Pending::next),Codec.INT.fieldOf("count").forGetter(Pending::count)
        ).apply(i,Pending::new));
    }
    public static final Codec<RaidStateData> CODEC=RecordCodecBuilder.create(i->i.group(
        Codec.LONG.fieldOf("last_raid_day").forGetter(s->s.lastRaidDay),
        Codec.LONG.fieldOf("active_day").forGetter(s->s.activeDay),
        Codec.STRING.listOf().optionalFieldOf("mob_ids",List.of()).forGetter(s->List.<String>of()),
        Codec.BOOL.optionalFieldOf("glow_started",false).forGetter(s->s.glowStarted),
        Codec.BOOL.optionalFieldOf("overworld_wave",false).forGetter(s->false),
        Codec.INT.optionalFieldOf("total_spawned",0).forGetter(s->s.totalSpawned),
        Codec.STRING.listOf().optionalFieldOf("persistent_raiders",List.of()).forGetter(s->List.<String>of()),
        Codec.STRING.listOf().optionalFieldOf("sunrise_cleanup",List.of()).forGetter(s->List.copyOf(s.cleanup)),
        Codec.unboundedMap(Codec.STRING,Raider.CODEC).optionalFieldOf("raiders",Map.of()).forGetter(s->Map.copyOf(s.raiders)),
        Pending.CODEC.listOf().optionalFieldOf("pending",List.of()).forGetter(s->List.copyOf(s.pending))
    ).apply(i,RaidStateData::new));
    static final SavedDataType<RaidStateData> TYPE=new SavedDataType<>(Identifier.fromNamespaceAndPath("beddefense","raid_state"),RaidStateData::new,CODEC,null);
    private long lastRaidDay,activeDay;
    private int totalSpawned;
    private boolean glowStarted;
    private final Map<String,Raider> raiders;
    private final List<Pending> pending;
    private final Set<String> cleanup;
    public RaidStateData(){this(0,-1,List.of(),false,false,0,List.of(),List.of(),Map.of(),List.of());}
    private RaidStateData(long last,long active,List<String> oldIds,boolean glow,boolean oldOverworld,int total,List<String> oldPersistent,List<String> cleanup,Map<String,Raider> raiders,List<Pending> pending){
        lastRaidDay=last;activeDay=active;glowStarted=glow;totalSpawned=total;
        this.raiders=new HashMap<>(raiders);this.pending=new ArrayList<>(pending);this.cleanup=new HashSet<>(cleanup);
        // Legacy records lacked dimension and ownership. Known Overworld waves settle globally;
        // unknown mixed waves are conservatively retained until ENTITY_LOAD supplies their dimension.
        oldIds.forEach(id->this.raiders.putIfAbsent(id,new Raider(oldOverworld?OVERWORLD:"unknown","",1,active)));
        oldPersistent.forEach(id->this.raiders.putIfAbsent(id,new Raider("unknown","",1,0)));
    }
    public static RaidStateData get(ServerLevel world){return world.getDataStorage().computeIfAbsent(TYPE);}
    public boolean active(){return activeDay>=1;}
    public long activeDay(){return activeDay;}
    public long lastRaidDay(){return lastRaidDay;}
    public boolean glowStarted(){return glowStarted;}
    public int totalSpawned(){return Math.max(totalSpawned,remaining());}
    public void glowStarted(boolean value){glowStarted=value;setDirty();}
    public Map<String,Raider> raiders(){return Map.copyOf(raiders);}
    public List<Pending> pending(){return List.copyOf(pending);}
    public int remaining(){return raiders.size()+pending.stream().mapToInt(Pending::count).sum();}
    public int activeCount(String dimension){return (int)raiders.values().stream().filter(r->r.dimension.equals(dimension)).count();}
    public boolean isRaider(UUID id){return raiders.containsKey(id.toString());}
    public Raider raider(UUID id){return raiders.get(id.toString());}
    public boolean pendingCleanup(UUID id){return cleanup.contains(id.toString());}
    public void completedCleanup(UUID id){if(cleanup.remove(id.toString()))setDirty();}
    public void loaded(UUID id,String dimension){
        Raider r=raider(id);if(r!=null&&!r.dimension.equals(dimension)){raiders.put(id.toString(),new Raider(dimension,r.owner,r.roster,r.wave));setDirty();}
    }
    public void launch(long day,List<Pending> batches){
        if(active()||day<=lastRaidDay)throw new IllegalStateException("Duplicate raid launch");
        lastRaidDay=day;activeDay=day;glowStarted=false;pending.addAll(batches);totalSpawned=remaining();setDirty();
    }
    public void spawned(Pending batch,UUID id){
        if(!pending.remove(batch))throw new IllegalStateException("Stale spawn batch");
        raiders.put(id.toString(),new Raider(batch.dimension,batch.owner,batch.roster,batch.wave));
        if(batch.count>1)pending.add(new Pending(batch.dimension,batch.owner,batch.roster,batch.wave,batch.next+1,batch.count-1));
        setDirty();
    }
    public void track(UUID id,Raider raider){raiders.put(id.toString(),raider);setDirty();}
    public void remove(UUID id){if(raiders.remove(id.toString())!=null)setDirty();}
    /** Atomically removes Overworld ledger entries BEFORE entity removal/any later death callback. */
    public Map<String,Integer> settleMorning(){return settleMorning(true);}
    /** Hard difficulty: queued mobs never spawned and cannot incur a dawn HP penalty. */
    public Map<String,Integer> settleMorning(boolean chargePending){
        Map<String,Integer> penalties=new HashMap<>();
        raiders.entrySet().removeIf(e->{
            Raider r=e.getValue();if(!r.dimension.equals(OVERWORLD))return false;
            penalties.merge(r.owner,1,Integer::sum);cleanup.add(e.getKey());return true;
        });
        pending.removeIf(p->{if(!p.dimension.equals(OVERWORLD))return false;if(chargePending)penalties.merge(p.owner,p.count,Integer::sum);return true;});
        activeDay=-1;glowStarted=false;totalSpawned=remaining();setDirty();return penalties;
    }
    public void finish(){activeDay=-1;glowStarted=false;totalSpawned=remaining();setDirty();}
    public void stop(){cleanup.addAll(raiders.keySet());raiders.clear();pending.clear();finish();}
    /** Preserve unload-cleanup UUIDs, but reset the day-one spawn ledger. */
    public void resetForNewRun(){
        cleanup.addAll(raiders.keySet());raiders.clear();pending.clear();
        lastRaidDay=0;activeDay=-1;totalSpawned=0;glowStarted=false;setDirty();
    }
}
