package com.darkkyoung.beddefense;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/**
 * Server authority for beacon recovery; does not trust client/inventory NBT.
 * Only the original bed owner can open/deposit/restore, and never more than twice.
 */
public final class RecoveryBeaconManager {
    private RecoveryBeaconManager(){}

    public static UUID ownerAt(ServerLevel world,BlockPos pos){
        if(!world.dimension().equals(Level.OVERWORLD))return null;
        for(var entry:SpawnBedData.get(world).positions().entrySet())
            if(entry.getValue().equals(pos) &&
                    BedVitalData.get(world).state(entry.getKey())==2 &&
                    RecoveryStateData.get(world).canRecover(entry.getKey()))
                return entry.getKey();
        return null;
    }
    public static boolean canInteract(ServerLevel world,BlockPos pos,UUID owner){
        return world.dimension().equals(Level.OVERWORLD) &&
                RecoveryBeaconRegistry.isRecoveryBlock(world,pos) &&
                owner.equals(ownerAt(world,pos)) &&
                ChallengeData.get(world).running() &&
                ChallengeData.get(world).playerState(owner)==ChallengeData.ALIVE;
    }
    public static boolean open(ServerPlayer player,ServerLevel world,BlockPos pos){
        if(!canInteract(world,pos,player.getUUID())){
            player.sendSystemMessage(Component.literal("§c본인의 복구 가능한 스폰 침대 신호기만 사용할 수 있습니다."));
            return false;
        }
        int stage=RecoveryStateData.get(world).nextCostStage(player.getUUID());
        Component name=Component.literal(stage==1?"§a[BedDefense 복구] 1차 · 에메랄드":"§a[BedDefense 복구] 2차 · 에메랄드 블록");
        player.openMenu(new SimpleMenuProvider(
                (id,inventory,viewer)->new RecoveryDispenserMenu(id,inventory,world,pos,player.getUUID()),name));
        player.sendSystemMessage(Component.literal(stage==1
                ?"§a복구 신호기: 가운데 침대 1개, 나머지 8칸 에메랄드 1개씩 넣으세요. (1차 HP 25)"
                :"§e복구 신호기: 가운데 침대 1개, 나머지 8칸 에메랄드 블록 1개씩 넣으세요. (2차 HP 50)"));
        return true;
    }
    /** Called after block removal, or on every reconciliation tick for saved worlds. */
    public static void ensure(ServerLevel world,UUID owner){
        if(!world.dimension().equals(Level.OVERWORLD))return;
        if(BedVitalData.get(world).state(owner)!=2 || !RecoveryStateData.get(world).canRecover(owner))return;
        if(!ChallengeData.get(world).running())return;
        BlockPos foot=SpawnBedData.get(world).getPosition(owner);
        if(foot==null||!world.isLoaded(foot))return;
        if(world.getBlockState(foot).isAir())
            world.setBlockAndUpdate(foot,RecoveryBeaconRegistry.BLOCK.defaultBlockState());
    }
    public static void tick(MinecraftServer server){
        ServerLevel world=server.overworld();
        long now=world.getGameTime();
        if(now%5!=0)return;
        for(var entry:SpawnBedData.get(world).positions().entrySet()){
            UUID owner=entry.getKey();
            if(BedVitalData.get(world).state(owner)==2){
                if(RecoveryStateData.get(world).canRecover(owner))ensure(world,owner);
                else if(world.isLoaded(entry.getValue()) &&
                        RecoveryBeaconRegistry.isRecoveryBlock(world,entry.getValue()))
                    world.setBlockAndUpdate(entry.getValue(),Blocks.AIR.defaultBlockState());
            }
        }
        for(ServerPlayer player:server.getPlayerList().getPlayers()){
            if(player.containerMenu instanceof RecoveryDispenserMenu menu &&
                    menu.owner().equals(player.getUUID())){
                if(!canInteract(menu.level(),menu.beacon(),menu.owner())){
                    player.closeContainer();
                    continue;
                }
                boolean second=RecoveryStateData.get(world).used(menu.owner())==1;
                if(menu.ingredientsReady(second)){
                    if(SpawnBedPrototype.reviveFromBeacon(player,world,menu.beacon(),
                            second?50:25)){
                        menu.consumeRequired(player);
                        player.closeContainer();
                    }else if(now%40==0){
                        player.sendSystemMessage(Component.literal(
                                "§c신호기 옆 침대 머리 공간과 바닥 지지 블록을 확보해 주세요. 재료는 보존됩니다."));
                    }
                }
            }
            if(now%100==0&&BedVitalData.get(world).state(player.getUUID())==2 &&
                    RecoveryStateData.get(world).canRecover(player.getUUID()) &&
                    ChallengeData.get(world).running()){
                BlockPos foot=SpawnBedData.get(world).getPosition(player.getUUID());
                if(foot!=null)player.connection.send(new ClientboundSetActionBarTextPacket(
                        Component.literal("§e스폰 침대 복구 위치: §f"+foot.getX()+" "+foot.getY()+" "+foot.getZ()
                                +" §7· 남은 복구 "+(2-RecoveryStateData.get(world).used(player.getUUID()))+"회")));
            }
        }
    }
    public static void clearAll(ServerLevel world){
        for(BlockPos foot:SpawnBedData.get(world).positions().values())
            if(world.isLoaded(foot)&&RecoveryBeaconRegistry.isRecoveryBlock(world,foot))
                world.setBlockAndUpdate(foot,Blocks.AIR.defaultBlockState());
    }
}
