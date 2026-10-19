package com.darkkyoung.beddefense;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

/**
 * Phase R0 single-player-first playable raid foundation.
 *
 * Initial 100-day counts follow the draft. Mixed type ratios are PROVISIONAL;
 * exact day-by-day composition, Siege AI and boss buffs will follow.
 *
 * An empty Spawn Bed is NOT a reason to skip a wave. Raider mobs hunt the player.
 */
public final class RaidManager {
    public static final String TAG="beddefense_raid_mob";
    public static final String OVERWORLD_TAG="beddefense_overworld_raid";
    public static final long BEDTIME_TICK=12542L;
    public static final long SUNRISE_TICK=23000L;
    public static final long GLOW_START_TICK=SUNRISE_TICK-6000L; // 5 real minutes at 20 tps
    private static int tickCounter;
    private static final int[] TOTALS={
5, 6, 6, 7, 8, 8, 9, 10, 11, 12, 9, 10, 11, 12, 13, 12, 14, 14, 15, 18, 13, 14, 15, 16, 16, 17, 18, 18, 19, 22, 16, 18, 18, 20, 20, 21, 21, 22, 23, 26, 20, 22, 22, 24, 24, 25, 26, 27, 28, 32, 24, 26, 26, 28, 28, 30, 30, 32, 32, 38, 28, 30, 32, 32, 34, 34, 36, 36, 38, 44, 34, 36, 38, 38, 40, 40, 42, 44, 46, 52, 40, 42, 44, 46, 48, 48, 50, 52, 54, 62, 46, 48, 50, 52, 54, 56, 58, 62, 68, 80
    };
    private static final Map<UUID,Long> LAST_BED_HIT=new HashMap<>();
    private RaidManager() {}

    public static boolean isActive(MinecraftServer server) {
        return RaidStateData.get(server.overworld()).active();
    }
    public static int totalForDay(int day) {
        return TOTALS[Math.max(1,Math.min(100,day))-1];
    }

    public static void onMobDeath(Entity mob, MinecraftServer server) {
        if(!mob.getTags().contains(TAG))return;
        RaidStateData state=RaidStateData.get(server.overworld());
        if(state.active())state.remove(mob.getUUID());
        LAST_BED_HIT.remove(mob.getUUID());
    }

    public static void tick(MinecraftServer server) {
        // Avoid full loaded-entity scans on every server tick.
        if(++tickCounter<20)return;
        tickCounter=0;
        ChallengeData challenge=ChallengeData.get(server.overworld());
        if(!challenge.isStarted())return;
        long clock=ChallengeMenu.overworldClock(server);
        long time=Math.floorMod(clock,24000L);
        int day=(int)Math.min(100,challenge.day(clock));
        RaidStateData state=RaidStateData.get(server.overworld());

        if(state.active()) {
            if(time>=SUNRISE_TICK || time<BEDTIME_TICK) {
                finishAtMorning(server,state);
                return;
            }
            // Previous wave can be cleared early. Nether/End persistent mobs remain
            // if not killed at sunrise, and can accumulate across days.
            if(state.remaining()==0) {
                broadcast(server,"§a[베드 디펜스] §f"+state.activeDay()+"일차 레이드 클리어");
                state.finish();
                return;
            }
            if(time>=GLOW_START_TICK && !state.glowStarted()) {
                glowBeforeMorning(server,state);
            }
            moveRaiders(server);
            return;
        }

        int interval=ChallengeOptionsData.get(server.overworld()).raidEveryDays();
        if(time>=BEDTIME_TICK && time<SUNRISE_TICK &&
                day>state.lastRaidDay() && (day-1)%interval==0) {
            launch(server,state,day);
        } else {
            moveRaiders(server); // remaining Nether/End raiders continue hunting
        }
    }

    private static void launch(MinecraftServer server,RaidStateData state,int day) {
        List<String> ids=new ArrayList<>();
        int intended=totalForDay(day);
        List<ServerPlayer> activePlayers=server.getPlayerList().getPlayers().stream()
                .filter(p->!p.isSpectator()).toList();
        if(activePlayers.isEmpty())return; // retry when a player is present
        for(ServerPlayer p:activePlayers) {
            for(int i=0;i<intended;i++) {
                String mobType=mobForDay(day,i,intended);
                UUID spawnId=spawn(server,p,mobType,day,i);
                if(spawnId!=null)ids.add(spawnId.toString());
            }
        }
        state.launch(day,ids,activePlayers.size()==1 && activePlayers.get(0).level().dimension().equals(Level.OVERWORLD));
        broadcast(server,"§c§l[베드 디펜스] §f"+day+"일차 레이드 시작! §7(생성 "+ids.size()+"마리)");
        run(server,"playsound minecraft:event.raid.horn master @a ~ ~ ~ 1 1");
    }

    private static String mobForDay(int day,int index,int total) {
        // Day 1: exactly five zombies. Each tier gradually mixes in vanilla mobs.
        if(day<=2)return "minecraft:zombie";
        if(day<=4)return index%3==0?"minecraft:spider":"minecraft:zombie";
        if(day<=10)return index%5==0?"minecraft:skeleton":
                (index%4==0?"minecraft:spider":"minecraft:zombie");
        if(day<=20)return index%13==0?"minecraft:creeper":
                (index%5==0?"minecraft:skeleton":
                 index%7==0?"minecraft:pillager":"minecraft:zombie");
        if(day<=40)return index%16==0?"minecraft:witch":
                index%9==0?"minecraft:creeper":
                index%6==0?"minecraft:pillager":
                index%5==0?"minecraft:spider":"minecraft:zombie";
        if(day<=60)return index%17==0?"minecraft:blaze":
                index%13==0?"minecraft:witch":
                index%11==0?"minecraft:vindicator":
                index%7==0?"minecraft:skeleton":"minecraft:zombie";
        return index%17==0?"minecraft:blaze":
                index%13==0?"minecraft:witch":
                index%11==0?"minecraft:vindicator":
                index%7==0?"minecraft:wither_skeleton":
                index%5==0?"minecraft:pillager":"minecraft:zombie";
    }

    private static UUID spawn(MinecraftServer server,ServerPlayer player,
                              String type,int day,int index) {
        if(!(player.level() instanceof ServerLevel level))return null;
        BlockPos near=player.blockPosition();
        if(level.dimension().equals(Level.OVERWORLD)) {
            BlockPos bed=SpawnBedPrototype.registeredPosition(player,server.overworld());
            if(bed!=null && level.isLoaded(bed))near=bed;
        }
        BlockPos pos=findSpawn(level,near);
        if(pos==null)return null;
        String unique="bd_"+UUID.randomUUID().toString().replace("-","");
        String dimension=level.dimension().identifier().toString();
        String tags="[\""+TAG+"\",\""+unique+"\""+
                (level.dimension().equals(Level.OVERWORLD)?",\""+OVERWORLD_TAG+"\"":"")+"]";
        String cmd="execute in "+dimension+" run summon "+type+" "+
                (pos.getX()+0.5)+" "+pos.getY()+" "+(pos.getZ()+0.5)+
                " {PersistenceRequired:1b,Tags:"+tags+"}";
        run(server,cmd);
        Entity spawned=null;
        for(Entity e:level.getAllEntities()){
            if(e.getTags().contains(unique)){spawned=e;break;}
        }
        if(spawned==null)return null;
        // Speed I from day 11, Speed II from day 25, Speed III from day 41.
        if(day>=11 && type.equals("minecraft:zombie") && index%3==0) {
            int amplifier=day>=41 && index%5==0 ? 2 : day>=25 && index%2==0 ? 1 : 0;
            run(server,"execute in "+dimension+" run effect give @e[tag="+unique+"] minecraft:speed 999999 "+amplifier+" true");
        }
        return spawned.getUUID();
    }

    private static BlockPos findSpawn(ServerLevel world,BlockPos near) {
        ThreadLocalRandom random=ThreadLocalRandom.current();
        for(int i=0;i<48;i++){
            int dx=random.nextInt(-18,19),dz=random.nextInt(-18,19);
            if(dx*dx+dz*dz<60)continue;
            for(int dy=3;dy>=-5;dy--){
                BlockPos at=near.offset(dx,dy,dz);
                if(world.isLoaded(at) && world.isEmptyBlock(at) &&
                        world.isEmptyBlock(at.above()) &&
                        world.getBlockState(at.below()).isSolid()) {
                    return at;
                }
            }
        }
        return null;
    }

    private static void glowBeforeMorning(MinecraftServer server,RaidStateData state) {
        state.glowStarted(true);
        for(ServerLevel level:server.getAllLevels()){
            for(Entity e:level.getAllEntities()){
                if(e.getTags().contains(TAG))e.setGlowingTag(true);
            }
        }
        broadcast(server,"§e[베드 디펜스] §f일출까지 5분! 남은 레이드 몬스터에게 발광 표시가 적용됩니다.");
    }

    private static void finishAtMorning(MinecraftServer server,RaidStateData state) {
        int outstanding=state.remaining();
        // Settle before players can kill leftover mobs in daylight. No item drops or XP.
        for(ServerLevel level:server.getAllLevels()){
            if(!level.dimension().equals(Level.OVERWORLD))continue;
            for(Entity e:level.getAllEntities()){
                if(!e.getTags().contains(OVERWORLD_TAG))continue;
                level.sendParticles(ParticleTypes.FLAME,e.getX(),e.getY()+0.6,e.getZ(),12,0.3,0.4,0.3,0.02);
                e.discard();
            }
        }
        // Temporary balance: 1 HP per outstanding Overworld raider, never a slow burn.
        if(outstanding>0 && state.overworldWave()){
            for(ServerPlayer p:server.getPlayerList().getPlayers()) {
                if(!p.level().dimension().equals(Level.OVERWORLD))continue;
                BedVitalData health=BedVitalData.get(server.overworld());
                if(health.state(p.getUUID())==1){
                    int remain=health.damage(p.getUUID(),outstanding);
                    p.sendSystemMessage(Component.literal("§c미처치 레이드 "+outstanding+"마리 · 침대 HP -"+outstanding+" (남은 "+remain+")"));
                    if(remain==0)bedDestroyed(server);
                }
            }
        }
        broadcast(server, outstanding==0 ? "§a레이드 클리어" :
                "§e아침입니다. 레이드 생존 · 미처치 "+outstanding+"마리 정산");
        // In Nether/End, any remaining enemy retains persistence into the next day.
        state.finish();
    }

    public static void bedDestroyed(MinecraftServer server) {
        run(server,"title @a times 10 70 20");
        run(server,"title @a title {\"text\":\"살아남으세요\",\"color\":\"red\",\"bold\":true}");
        run(server,"playsound minecraft:entity.wither.spawn master @a ~ ~ ~ 1 0.75");
    }

    private static void moveRaiders(MinecraftServer server) {
        int attackers=0;
        for(ServerLevel level:server.getAllLevels()){
            for(Entity e:level.getAllEntities()){
                if(!(e instanceof Mob mob)||!e.getTags().contains(TAG))continue;
                ServerPlayer nearest=null;
                double min=Double.MAX_VALUE;
                for(ServerPlayer p:server.getPlayerList().getPlayers()) {
                    if(p.level()!=level||p.isSpectator())continue;
                    double dist=p.distanceToSqr(mob);
                    if(dist<min){min=dist;nearest=p;}
                }
                if(nearest==null)continue;
                BlockPos bed=level.dimension().equals(Level.OVERWORLD)
                        ? SpawnBedPrototype.registeredPosition(nearest,server.overworld()) : null;
                if(bed==null) {
                    mob.setTarget(nearest);
                    continue;
                }
                // Spawn Bed > player. Siege block breaking/placing AI still future work.
                mob.setTarget(null);
                if(mob.distanceToSqr(bed.getX()+0.5,bed.getY()+0.5,bed.getZ()+0.5)>7.0) {
                    mob.getNavigation().moveTo(bed.getX()+0.5,bed.getY(),bed.getZ()+0.5,1.05);
                } else if(attackers<2) {
                    attackers++;
                    long now=level.getGameTime();
                    long interval=(long)Math.max(20,Math.round(80.0*Math.pow(0.93,
                        Math.floorDiv(Math.max(1,ChallengeData.get(server.overworld()).day(ChallengeMenu.overworldClock(server)))-1,10))));
                    if(now-LAST_BED_HIT.getOrDefault(mob.getUUID(),0L)>=interval) {
                        LAST_BED_HIT.put(mob.getUUID(),now);
                        int dmg=ChallengeData.get(server.overworld()).day(ChallengeMenu.overworldClock(server))>=81?3:
                            ChallengeData.get(server.overworld()).day(ChallengeMenu.overworldClock(server))>=51?2:1;
                        BedVitalData vitals=BedVitalData.get(server.overworld());
                        int next=vitals.damage(nearest.getUUID(),dmg);
                        if(next==0)bedDestroyed(server);
                    }
                }
            }
        }
    }

    private static void broadcast(MinecraftServer server,String message) {
        server.getPlayerList().broadcastSystemMessage(Component.literal(message),false);
    }
    private static void run(MinecraftServer server,String command) {
        try {
            server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withSuppressedOutput(),command);
        } catch(Exception e){BedDefense.LOGGER.warn("Raid command failed: {}",command,e);}
    }
}
