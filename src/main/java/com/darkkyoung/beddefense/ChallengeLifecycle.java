package com.darkkyoung.beddefense;

import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;

/** All death/respawn decisions are persisted before entity replacement or disconnect. */
public final class ChallengeLifecycle {
    private ChallengeLifecycle(){}
    public static void init(){ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer,newPlayer,alive)->{if(!alive)respawn(newPlayer);});}
    public static void join(ServerPlayer player,MinecraftServer server){
        ChallengeData data=ChallengeData.get(server.overworld());
        if(data.running()&&!player.isSpectator())data.join(player.getUUID());
        if(data.isStarted()&&(data.playerState(player.getUUID())==ChallengeData.ELIMINATED||data.outcome()==ChallengeData.LOST))player.setGameMode(GameType.SPECTATOR);
        // Missing/unregistered beds never trigger the dramatic raid-destruction warning.
    }
    public static void tick(MinecraftServer server){
        ChallengeData data=ChallengeData.get(server.overworld());if(!data.running())return;
        data.observeDay(ChallengeMenu.overworldClock(server));
        // Risky missing-bed state is represented by the hearts/HUD, not a title.
        // Day 100 is fully playable. Crossing into day 101 at world clock 0 ends the run.
        if(data.day(ChallengeMenu.overworldClock(server))>100)finish(server,false,"100일 제한 초과 (101일차 시작)");
    }
    public static void death(ServerPlayer player){
        MinecraftServer server=((net.minecraft.server.level.ServerLevel)player.level()).getServer();
        ChallengeData data=ChallengeData.get(server.overworld());UUID id=player.getUUID();
        if(!data.running()||!data.participating(id)||data.playerState(id)!=ChallengeData.ALIVE)return;
        SpawnBedPrototype.status(player,server.overworld());
        BedVitalData bed=BedVitalData.get(server.overworld());
        if(bed.state(id)!=1){
            data.playerState(id,ChallengeData.ELIMINATED);
            player.sendSystemMessage(Component.literal("§c스폰 침대 없이 사망하여 탈락했습니다."));
            if(data.allEliminated())finish(server,false,"스폰 침대 없이 사망");
            return;
        }
        // Record allowance first. A repeated death event cannot charge HP or add another allowance.
        data.playerState(id,ChallengeData.RESPAWNING);
        if(!player.level().dimension().equals(Level.OVERWORLD)){
            if(SpawnBedPrototype.damage(server,id,ChallengeBalance.DIMENSION_DEATH_HP,"dimension_death")==0)
                data.playerState(id,ChallengeData.FINAL_RESPAWN);
        }
    }
    public static void respawn(ServerPlayer player){
        MinecraftServer server=((net.minecraft.server.level.ServerLevel)player.level()).getServer();
        ChallengeData data=ChallengeData.get(server.overworld());UUID id=player.getUUID();
        if(!data.isStarted())return;
        int state=data.playerState(id);
        if(state==ChallengeData.ELIMINATED||data.outcome()==ChallengeData.LOST){player.setGameMode(GameType.SPECTATOR);return;}
        if(state==ChallengeData.RESPAWNING||state==ChallengeData.FINAL_RESPAWN){
            // Consume exactly once, even when the original bed was destroyed by this death.
            data.playerState(id,ChallengeData.ALIVE);
            BlockPos foot=SpawnBedData.get(server.overworld()).getPosition(id);
            if(foot!=null){
                BlockPos safe=SpawnBedPrototype.safeRespawn(server.overworld(),foot);
                RaidManager.run(server,"execute in minecraft:overworld run tp "+id+" "+(safe.getX()+.5)+" "+safe.getY()+" "+(safe.getZ()+.5));
            }
            // Dimension penalty/final respawn must not impersonate a direct raid bed kill.
        }
    }
    /**
     * Prepare another challenge in the SAME persistent world.
     * This is allowed only after a terminal outcome and only when the explicit
     * setup countdown has finished. It does NOT erase builds or regular items.
     */
    public static boolean resetFinishedRun(MinecraftServer server){
        ServerLevel world=server.overworld();
        ChallengeData run=ChallengeData.get(world);
        if(!run.isStarted())return true;
        if(!run.canRestart())return false;

        java.util.Set<UUID> restore=new java.util.HashSet<>();
        for(ServerPlayer p:server.getPlayerList().getPlayers()) {
            if(p.isSpectator() && (run.playerState(p.getUUID())==ChallengeData.ELIMINATED ||
                    (run.outcome()==ChallengeData.LOST && run.participating(p.getUUID()))))
                restore.add(p.getUUID());
        }

        // Retire old raids, retaining pending unload cleanup IDs so mobs from
        // old chunks can never rejoin a new run.
        RaidManager.stop(server);
        RaidStateData.get(world).resetForNewRun();
        SpawnBedGlow.clearAll(world);
        BedVitalData.get(world).resetForNewRun();
        SpawnBedData.get(world).resetForNewRun();
        if(!run.resetFinishedRun())return false;

        for(ServerPlayer p:server.getPlayerList().getPlayers()){
            if(restore.contains(p.getUUID()))p.setGameMode(GameType.SURVIVAL);
            // Old special-bed items are ordinary beds in the next challenge.
            // Clear only our own bed token and name; keep every ordinary item.
            for(int i=0;i<p.getInventory().getContainerSize();i++){
                var item=p.getInventory().getItem(i);
                if(SpawnBedPrototype.isPortable(item)){
                    item.remove(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
                    item.remove(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
                }
            }
            // An old run's respawn point may not be reused until the new run
            // deliberately registers its first unique bed.
            p.setRespawnPosition(null,false);
        }
        return true;
    }

    public static void dragonKilled(MinecraftServer server){if(ChallengeData.get(server.overworld()).running())finish(server,true,"엔더 드래곤 처치");}
    public static void finish(MinecraftServer server,boolean won,String reason){
        ChallengeData data=ChallengeData.get(server.overworld());
        if(!data.finish(won?ChallengeData.WON:ChallengeData.LOST,server.overworld().getGameTime(),reason))return;
        RaidManager.stop(server);
        server.getPlayerList().broadcastSystemMessage(Component.literal((won?"§a§l챌린지 성공! ":"§c§l챌린지 실패 · ")+reason),false);
        RaidManager.run(server,"title @a times 10 120 20");
        RaidManager.run(server,"title @a title {\"text\":\""+(won?"챌린지 성공!":"챌린지 실패")+"\",\"color\":\""+(won?"green":"red")+"\",\"bold\":true}");
        if(!won)for(ServerPlayer player:server.getPlayerList().getPlayers())if(player.isAlive())player.setGameMode(GameType.SPECTATOR);
    }
}
