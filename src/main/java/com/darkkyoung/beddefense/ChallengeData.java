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

/** World lifecycle. Optional fields migrate the alpha.4/PR1 schema without resetting a run. */
public final class ChallengeData extends SavedData {
    public static final int READY=0, RUNNING=1, WON=2, LOST=3;
    // Participant states: alive, death awaiting respawn, final death awaiting respawn, eliminated.
    public static final int ALIVE=0, RESPAWNING=1, FINAL_RESPAWN=2, ELIMINATED=3;
    public static final int PROFILE_LEGACY=0, PROFILE_EASY=1, PROFILE_HARD=2;
    public static final Codec<ChallengeData> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.LONG.fieldOf("start_game_tick").forGetter(d->d.startGameTick),
        Codec.LONG.fieldOf("start_day_index").forGetter(d->d.startDayIndex),
        Codec.INT.optionalFieldOf("outcome",0).forGetter(d->d.outcome),
        Codec.LONG.optionalFieldOf("stopped_elapsed",-1L).forGetter(d->d.stoppedElapsed),
        Codec.LONG.optionalFieldOf("last_day",1L).forGetter(d->d.lastDay),
        Codec.unboundedMap(Codec.STRING,Codec.INT).optionalFieldOf("participants",Map.of()).forGetter(d->Map.copyOf(d.players)),
        Codec.STRING.optionalFieldOf("result_reason","").forGetter(d->d.reason),
        Codec.INT.optionalFieldOf("raid_balance_profile",0).forGetter(d->d.raidProfile)
    ).apply(i,ChallengeData::new));
    static final SavedDataType<ChallengeData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath("beddefense","challenge"),ChallengeData::new,CODEC,null);
    private long startGameTick,startDayIndex,stoppedElapsed,lastDay;
    private int outcome;
    private String reason;
    private int raidProfile;
    private final Map<String,Integer> players;
    public ChallengeData() {this(-1,-1,0,-1,1,Map.of(),"",PROFILE_LEGACY);}
    private ChallengeData(long tick,long day,int result,long stopped,long last,Map<String,Integer> players,String reason,int raidProfile) {
        this.raidProfile=raidProfile;
        startGameTick=tick;startDayIndex=day;outcome=result==0&&tick>=0?RUNNING:result;
        stoppedElapsed=stopped;lastDay=last;this.players=new HashMap<>(players);this.reason=reason;
    }
    public static ChallengeData get(ServerLevel world) {return world.getDataStorage().computeIfAbsent(TYPE);}
    public boolean isStarted(){return startGameTick>=0;}
    /** Completed challenges may be restarted; resetting never silently rewinds terrain. */
    public boolean canRestart(){return isStarted()&&!running();}
    public boolean resetFinishedRun(){
        if(!canRestart())return false;
        startGameTick=-1;startDayIndex=-1;stoppedElapsed=-1;lastDay=1;
        outcome=READY;reason="";raidProfile=PROFILE_LEGACY;players.clear();setDirty();return true;
    }
    public boolean running(){return outcome==RUNNING;}
    public int outcome(){return outcome;}
    public String reason(){return reason;}
    public int raidProfile(){return raidProfile;}
    public boolean hardRaidProfile(){return raidProfile==PROFILE_HARD;}
    public boolean start(long tick,long clock){return start(tick,clock,PROFILE_LEGACY);}
    public boolean start(long tick,long clock,int profile){
        if(isStarted() || profile<PROFILE_LEGACY || profile>PROFILE_HARD)return false;
        startGameTick=tick;startDayIndex=Math.floorDiv(clock,24000);outcome=RUNNING;lastDay=1;
        raidProfile=profile;setDirty();return true;
    }
    public long elapsedTicks(long tick){return !isStarted()?-1:stoppedElapsed>=0?stoppedElapsed:Math.max(0,tick-startGameTick);}
    public long day(long clock){return !isStarted()?-1:running()?Math.max(lastDay,Math.floorDiv(clock,24000)-startDayIndex+1):lastDay;}
    public void observeDay(long clock){long day=day(clock);if(day>lastDay){lastDay=day;setDirty();}}
    public void debugDay(long day,long clock){startDayIndex=Math.floorDiv(clock,24000)-day+1;lastDay=day;setDirty();}
    public void join(UUID id){if(running()&&!players.containsKey(id.toString())){players.put(id.toString(),ALIVE);setDirty();}}
    public int playerState(UUID id){return players.getOrDefault(id.toString(),ALIVE);}
    public boolean participating(UUID id){return players.containsKey(id.toString())&&playerState(id)!=ELIMINATED;}
    public void playerState(UUID id,int state){players.put(id.toString(),state);setDirty();}
    public boolean allEliminated(){return !players.isEmpty()&&players.values().stream().allMatch(v->v==ELIMINATED);}
    public boolean finish(int result,long tick,String why){
        if(!running())return false;
        stoppedElapsed=elapsedTicks(tick);outcome=result;reason=why;setDirty();return true;
    }
}
