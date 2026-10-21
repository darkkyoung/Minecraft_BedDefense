package com.darkkyoung.beddefense;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/** Daily raid scheduler; persistent accounting and per-mob targeting run on the server. */
public final class RaidManager {
    public static final long BEDTIME_TICK=12542L,SUNRISE_TICK=23000L,GLOW_START_TICK=17000L;
    private static final Map<UUID,Long> LAST_BED_HIT=new HashMap<>();
    private static int pathCursor;
    private static final Map<UUID,BlockPos> BED_TARGETS=new HashMap<>();
    private static final Set<Mob> GOALS=Collections.newSetFromMap(new WeakHashMap<>());
    public static BlockPos bedTarget(UUID mob){return BED_TARGETS.get(mob);}
    private static void attachGoal(Mob mob){if(GOALS.add(mob))((com.darkkyoung.beddefense.mixin.MobGoalAccessor)(Object)mob).beddefense$goals().addGoal(0,new RaidBedGoal(mob));}
    private RaidManager(){}
    public static boolean isActive(MinecraftServer server){return RaidStateData.get(server.overworld()).active();}
    public static void onMobDeath(Entity mob,MinecraftServer server){
        RaidStateData.get(server.overworld()).remove(mob.getUUID());LAST_BED_HIT.remove(mob.getUUID());BED_TARGETS.remove(mob.getUUID());SiegeController.clear(mob.getUUID());
    }
    public static void reset(){LAST_BED_HIT.clear();BED_TARGETS.clear();GOALS.clear();pathCursor=0;SiegeController.reset();}
    public static int totalForDay(int day){return TOTALS[Math.max(1,Math.min(100,day))-1];}
    public static int totalForDay(int day,boolean hard){return hard?HardRaidBalance.totalForDay(day):totalForDay(day);}
    public static boolean hardMode(MinecraftServer server){
        return ChallengeData.get(server.overworld()).hardRaidProfile();
    }
    /** START_SERVER_TICK settles before entity ticks/deaths can change the dawn snapshot. */
    public static void tick(MinecraftServer server){
        ChallengeData challenge=ChallengeData.get(server.overworld());
        if(!challenge.running())return;
        long clock=ChallengeMenu.overworldClock(server),time=Math.floorMod(clock,24000);
        int day=(int)challenge.day(clock);
        RaidStateData state=RaidStateData.get(server.overworld());
        if(state.active()&&(time>=SUNRISE_TICK||time<BEDTIME_TICK||day>state.activeDay()))finishAtMorning(server,state);
        if(day>100)return;
        if(time>=BEDTIME_TICK&&time<SUNRISE_TICK&&day>state.lastRaidDay())launch(server,day);
        SiegeController.tick(server);
        if(server.overworld().getGameTime()%20!=0)return;
        if(state.active()&&time>=GLOW_START_TICK&&!state.glowStarted()){
            state.glowStarted(true);broadcast(server,"§e일출까지 5분! 남은 레이드 몬스터가 발광합니다.");
        }
        spawnPending(server,state);
        moveRaiders(server,state);
        if(state.active()&&state.remaining()==0){broadcast(server,"§a레이드 클리어!");state.finish();}
        SiegeController.cleanup(server.overworld().getGameTime());
    }
    public static boolean launch(MinecraftServer server,int day){
        ChallengeData challenge=ChallengeData.get(server.overworld());
        RaidStateData state=RaidStateData.get(server.overworld());
        if(!challenge.running()||state.active()||day<=state.lastRaidDay()||day>100)return false;
        int roster=ChallengeBalance.rosterDay(day,ChallengeOptionsData.get(server.overworld()).raidStride());
        boolean hard=hardMode(server);
        List<RaidStateData.Pending> batches=new ArrayList<>();
        for(ServerPlayer p:server.getPlayerList().getPlayers())
            if(p.isAlive()&&!p.isSpectator()&&challenge.participating(p.getUUID()))
                batches.add(new RaidStateData.Pending(p.level().dimension().identifier().toString(),p.getUUID().toString(),roster,day,0,totalForDay(roster,hard)));
        if(batches.isEmpty())return false;
        state.launch(day,batches);
        broadcast(server,"§c§l"+day+"일차 레이드 시작! §f적용 편성 "+roster+"일차");
        run(server,"execute as @a at @s run playsound minecraft:event.raid.horn master @s ~ ~ ~ 1 1");
        return true;
    }
    private static void spawnPending(MinecraftServer server,RaidStateData state){
        int budget=ChallengeBalance.SPAWNS_PER_SECOND;
        // Compact one batch per player/night; no retry can silently lose an intended raider.
        for(RaidStateData.Pending batch:state.pending()){
            if(budget<=0)break;
            if(state.activeCount(batch.dimension())>=ChallengeBalance.ACTIVE_RAIDERS_PER_DIMENSION)continue;
            ServerPlayer owner;
            try{owner=server.getPlayerList().getPlayer(UUID.fromString(batch.owner()));}catch(IllegalArgumentException e){continue;}
            if(owner==null||owner.isSpectator()||!owner.level().dimension().identifier().toString().equals(batch.dimension()))continue;
            RaidStateData.Pending current=batch;
            for(int n=0;n<batch.count()&&budget>0&&state.activeCount(batch.dimension())<ChallengeBalance.ACTIVE_RAIDERS_PER_DIMENSION;n++){
                budget--;
                boolean hard=hardMode(server);
                UUID id=spawn(server,owner,mobForDay(batch.roster(),current.next(),totalForDay(batch.roster(),hard),hard),batch.roster(),current.next(),hard);
                if(id==null)break;
                state.spawned(current,id);
                current=new RaidStateData.Pending(batch.dimension(),batch.owner(),batch.roster(),batch.wave(),current.next()+1,current.count()-1);
            }
        }
    }
    public static void onLoad(Entity entity,ServerLevel level){
        RaidStateData state=RaidStateData.get(level.getServer().overworld());
        if(state.pendingCleanup(entity.getUUID())){entity.discard();state.completedCleanup(entity.getUUID());return;}
        state.loaded(entity.getUUID(),level.dimension().identifier().toString());
        if(entity instanceof Mob mob&&state.isRaider(entity.getUUID())){attachGoal(mob);applyAwareness(mob);}
        if(state.isRaider(entity.getUUID())&&state.glowStarted())entity.setGlowingTag(true);
    }
    public static void finishAtMorning(MinecraftServer server,RaidStateData state){
        // In hard mode, monsters that never actually spawned cannot inflict HP damage.
        Map<String,Integer> penalties=state.settleMorning(!hardMode(server));
        // Ledger is already settled. A later death or chunk load cannot reduce this immutable snapshot.
        penalties.forEach((owner,count)->{
            if(owner.isEmpty()){
                SpawnBedData.get(server.overworld()).positions().keySet().forEach(id->penalty(server,id,count));
            }else penalty(server,UUID.fromString(owner),count);
        });
        removePendingEntities(server,state);
        broadcast(server,"§e아침 정산 · 오버월드 미처치 "+penalties.values().stream().mapToInt(Integer::intValue).sum()+"마리 · 네더/엔드 잔여 "+state.remaining()+"마리");
    }
    private static void penalty(MinecraftServer server,UUID owner,int count){
        SpawnBedPrototype.damage(server,owner,count*ChallengeBalance.SUNRISE_HP_PER_SURVIVOR,"sunrise");
        ServerPlayer p=server.getPlayerList().getPlayer(owner);
        if(p!=null)p.sendSystemMessage(Component.literal("§c미처치 "+count+"마리 · 침대 HP 패널티 "+count*ChallengeBalance.SUNRISE_HP_PER_SURVIVOR));
    }
    public static void stop(MinecraftServer server){RaidStateData state=RaidStateData.get(server.overworld());state.stop();removePendingEntities(server,state);reset();}
    private static void removePendingEntities(MinecraftServer server,RaidStateData state){
        for(ServerLevel level:server.getAllLevels()){
            List<Entity> remove=new ArrayList<>();
            for(Entity entity:level.getAllEntities())if(state.pendingCleanup(entity.getUUID()))remove.add(entity);
            for(Entity entity:remove){SiegeController.clear(entity.getUUID());entity.discard();state.completedCleanup(entity.getUUID());LAST_BED_HIT.remove(entity.getUUID());}
        }
    }
    public static void bedDestroyed(MinecraftServer server){for(ServerPlayer p:server.getPlayerList().getPlayers())survivalWarning(p,server);}
    public static void survivalWarning(ServerPlayer player,MinecraftServer server){
        // EntityArgument.players does not resolve a bare UUID as an online player's name.
        // Send to the known connection, including remote-dimension and offline-mode players.
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(5,60,20));
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
            Component.literal("살아남으세요").withStyle(net.minecraft.ChatFormatting.RED,net.minecraft.ChatFormatting.BOLD)));
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(
            net.minecraft.core.Holder.direct(net.minecraft.sounds.SoundEvents.WITHER_SPAWN),net.minecraft.sounds.SoundSource.MASTER,
            player.getX(),player.getY(),player.getZ(),1F,.75F,server.overworld().getRandom().nextLong()));
    }
    static void moveRaiders(MinecraftServer server,RaidStateData state){
        List<Mob> loaded=new ArrayList<>();
        for(ServerLevel level:server.getAllLevels())
            for(Entity entity:level.getAllEntities())if(entity instanceof Mob mob&&state.isRaider(entity.getUUID()))loaded.add(mob);
        if(loaded.isEmpty())return;
        int first=Math.floorMod(pathCursor,loaded.size());pathCursor+=ChallengeBalance.PATH_REQUESTS_PER_SECOND;
        for(int i=0;i<loaded.size();i++){
            Mob mob=loaded.get((first+i)%loaded.size());ServerLevel level=(ServerLevel)mob.level();
            RaidStateData.Raider record=state.raider(mob.getUUID());
            int roster=record.roster();
            if(state.glowStarted())mob.setGlowingTag(true);
            double range=mob.getAttributeValue(Attributes.FOLLOW_RANGE);
            double playerDistance=range*range,bedDistance=range*range;
            ServerPlayer closest=null;UUID bedOwner=null;BlockPos bedPos=null;
            for(ServerPlayer p:server.getPlayerList().getPlayers()){
                if(p.level()!=level||!p.isAlive()||p.isSpectator()||!ChallengeData.get(server.overworld()).participating(p.getUUID()))continue;
                double distance=p.distanceToSqr(mob);if(distance<playerDistance){closest=p;playerDistance=distance;}
            }
            if(level.dimension().equals(Level.OVERWORLD))
                for(var entry:SpawnBedData.get(server.overworld()).positions().entrySet()){
                    if(BedVitalData.get(server.overworld()).state(entry.getKey())!=1)continue;
                    BlockPos pos=entry.getValue();double distance=mob.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5);
                    if(distance<bedDistance){bedDistance=distance;bedOwner=entry.getKey();bedPos=pos;}
                }
            boolean canPath=i<ChallengeBalance.PATH_REQUESTS_PER_SECOND;
            if(bedPos!=null&&ChallengeBalance.preferBed(playerDistance,bedDistance)){
                BED_TARGETS.put(mob.getUUID(),bedPos);
                mob.setTarget(null);
                var hit=level.clip(new net.minecraft.world.level.ClipContext(mob.getEyePosition(),net.minecraft.world.phys.Vec3.atCenterOf(bedPos),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,mob));
                boolean visible=hit.getType()==net.minecraft.world.phys.HitResult.Type.MISS||level.getBlockState(hit.getBlockPos()).getBlock() instanceof net.minecraft.world.level.block.BedBlock;
                if(bedDistance<=7&&visible){
                    SiegeController.clear(mob.getUUID());mob.getNavigation().stop();
                    long now=level.getGameTime();int interval=new int[]{80,70,60,50,46,60,50,44,56,50}[(roster-1)/10];
                    if(now-LAST_BED_HIT.getOrDefault(mob.getUUID(),-10000L)>=interval){
                        LAST_BED_HIT.put(mob.getUUID(),now);SpawnBedPrototype.damage(server,bedOwner,roster>=81?3:roster>=51?2:1,"raid");
                        mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND,net.minecraft.world.item.component.SwingAnimation.DEFAULT);
                        ServerPlayer owner=server.getPlayerList().getPlayer(bedOwner);
                        if(owner!=null)owner.connection.send(new net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket(Component.literal("§c§l스폰 침대 공격 받는 중!")));
                    }
                }else{if(canPath)mob.getNavigation().moveTo(bedPos.getX()+.5,bedPos.getY(),bedPos.getZ()+.5,1.15);SiegeController.attempt(mob,level,bedPos,roster,hardMode(server));}
            }else if(closest!=null){
                BED_TARGETS.remove(mob.getUUID());mob.setTarget(closest);
                if(canPath)mob.getNavigation().moveTo(closest,1.2);SiegeController.attempt(mob,level,closest.blockPosition(),roster,hardMode(server));
            }else{BED_TARGETS.remove(mob.getUUID());mob.setTarget(null);SiegeController.clear(mob.getUUID());}
        }
    }
    private static void broadcast(MinecraftServer server,String message){server.getPlayerList().broadcastSystemMessage(Component.literal(message),false);}
    public static void run(MinecraftServer server,String command){server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),command);}
    private static final int[] TOTALS={
5, 6, 6, 7, 8, 8, 9, 10, 11, 12, 9, 10, 11, 12, 13, 12, 14, 14, 15, 18, 13, 14, 15, 16, 16, 17, 18, 18, 19, 22, 16, 18, 18, 20, 20, 21, 21, 22, 23, 26, 20, 22, 22, 24, 24, 25, 26, 27, 28, 32, 24, 26, 26, 28, 28, 30, 30, 32, 32, 38, 28, 30, 32, 32, 34, 34, 36, 36, 38, 44, 34, 36, 38, 38, 40, 40, 42, 44, 46, 52, 40, 42, 44, 46, 48, 48, 50, 52, 54, 62, 46, 48, 50, 52, 54, 56, 58, 62, 68, 80
    };
    public static String mobForDay(int day,int index,int total,boolean hard) {
        return hard?HardRaidBalance.mobForDay(day,index):mobForDay(day,index,total);
    }
    public static String mobForDay(int day,int index,int total) {
        if(day%10==0 && index==total-1)return day>=60?"minecraft:ravager":day>=30?"minecraft:evoker":"minecraft:vindicator";
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
                              String type,int day,int index,boolean hard) {
        if(!(player.level() instanceof ServerLevel level))return null;
        BlockPos near=player.blockPosition();
        if(level.dimension().equals(Level.OVERWORLD)) {
            BlockPos bed=SpawnBedPrototype.registeredPosition(player,server.overworld());
            if(bed!=null && level.isLoaded(bed))near=bed;
        }
        BlockPos pos=findSpawn(level,near);
        if(pos==null)return null;
        String vanillaType=HardRaidBalance.BABY_ZOMBIE.equals(type)?"minecraft:zombie":type;
        var entityType=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(net.minecraft.resources.Identifier.parse(vanillaType));
        if(entityType==null)return null;
        Entity spawned=entityType.spawn(level,pos,net.minecraft.world.entity.EntitySpawnReason.EVENT);
        if(!(spawned instanceof Mob raidMob)){if(spawned!=null)spawned.discard();return null;}
        configureRaider(raidMob,day,index,type,hard);attachGoal(raidMob);
        return spawned.getUUID();
    }
    public static int speedAmplifier(String type,int day,int index){
        boolean eligible=Set.of("minecraft:zombie","minecraft:husk","minecraft:spider","minecraft:cave_spider","minecraft:skeleton","minecraft:stray","minecraft:pillager","minecraft:vindicator","minecraft:wither_skeleton").contains(type);
        if(!eligible)return -1;
        if(day>=51)return 2;
        if(day>=41&&index%5==0)return 2;
        if(day>=11&&index%3==0)return day>=25&&index%2==0?1:0;
        return -1;
    }
    private static void applyAwareness(Mob mob){
        var range=mob.getAttribute(Attributes.FOLLOW_RANGE);
        var id=net.minecraft.resources.Identifier.fromNamespaceAndPath("beddefense","awareness");
        if(range!=null){range.removeModifier(id);range.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(id,mob.level().dimension().equals(Level.OVERWORLD)?1:4,net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_BASE));}
    }
    public static void configureRaider(Mob mob,int day,int index,String type){
        configureRaider(mob,day,index,type,false);
    }
    public static void configureRaider(Mob mob,int day,int index,String type,boolean hard){
        mob.setPersistenceRequired();
        applyAwareness(mob);
        if(hard) HardRaidBalance.configure(mob,day,index,type);
        else {
            int speed=speedAmplifier(type,day,index);
            if(speed>=0)mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SPEED,-1,speed,false,false));
        }
        if(day%10==0&&index==totalForDay(day,hard)-1){
            mob.setCustomName(Component.literal("§c"+day+"일차 대장"));mob.setCustomNameVisible(true);
            mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.STRENGTH,-1,1,false,false));
            mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.RESISTANCE,-1,0,false,false));
        }
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

}
