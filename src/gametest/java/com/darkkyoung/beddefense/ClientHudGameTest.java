package com.darkkyoung.beddefense;

import com.darkkyoung.beddefense.client.BedDefenseHud;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Actual client rendering/networking smoke; screenshots supplement, not replace, human acceptance. */
public class ClientHudGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context){
        try(var world=context.worldBuilder().create()){
            world.getConnection().waitForChunksRender();
            context.runOnClient(mc->mc.options.guiScale().set(2));
            world.getServer().runOnServer(server->server.getPlayerList().getPlayers().getFirst().setGameMode(GameType.SURVIVAL));
            context.runOnClient(mc->mc.player.connection.sendCommand("beddefense setup"));context.waitTicks(3);
            context.runOnClient(mc->mc.player.connection.sendCommand("beddefense setup time reset"));
            context.runOnClient(mc->mc.player.connection.sendCommand("beddefense setup stride 2"));
            // Exercise actual client-side chat closing on the setup confirmation.
            context.runOnClient(mc->mc.gui.setScreen(new net.minecraft.client.gui.screens.ChatScreen("", false)));
            context.runOnClient(mc->mc.player.connection.sendCommand("beddefense setup confirm"));
            context.waitFor(mc->mc.gui.screen()==null);
            context.waitTicks(65);context.waitFor(mc->BedDefenseHud.hardcoreRisk());
            context.runOnClient(mc->{if("살아남으세요".equals(title(mc)))
                throw new AssertionError("No bed at challenge start must NOT display raid destruction title");});
            context.runOnClient(mc->{
                if(!"평원".equals(net.minecraft.network.chat.Component.translatable("beddefense.biome.minecraft.plains").getString()))
                    throw new AssertionError("Korean biome fallback is missing in the default English client");
            });
            context.takeScreenshot("r1-unregistered-hardcore");
            context.runOnClient(mc->mc.gui.hud.clearTitles());
            world.getServer().runOnServer(server->{
                var p=server.getPlayerList().getPlayers().getFirst();var level=server.overworld();BlockPos foot=p.blockPosition().offset(2,0,0);
                level.setBlock(foot.below(),Blocks.STONE.defaultBlockState(),3);level.setBlock(foot.south().below(),Blocks.STONE.defaultBlockState(),3);
                var state=BuiltInRegistries.BLOCK.getValue(Identifier.parse("minecraft:white_bed")).defaultBlockState().setValue(BedBlock.FACING,Direction.SOUTH);
                level.setBlock(foot,state.setValue(BedBlock.PART,BedPart.FOOT),2);level.setBlock(foot.south(),state.setValue(BedBlock.PART,BedPart.HEAD),2);
                SpawnBedPrototype.onUse(p,level,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(foot),Direction.UP,foot,false));
            });
            context.waitFor(mc->!BedDefenseHud.hardcoreRisk());
            world.getServer().runCommand("bossbar add beddefense:test {\"text\":\"보스 게이지 겹침 검사\"}");
            world.getServer().runCommand("bossbar set beddefense:test players @a");
            context.waitTicks(20);context.takeScreenshot("r1-placed-normal-hearts");
            world.getServer().runCommand("bossbar remove beddefense:test");
            world.getServer().runOnServer(server->{var p=server.getPlayerList().getPlayers().getFirst();SpawnBedPrototype.damage(server,p.getUUID(),37,"client_test");});
            context.waitTicks(12);context.takeScreenshot("r1-damaged-hp");
            world.getServer().runOnServer(server->SpawnBedPrototype.pickup(server.getPlayerList().getPlayers().getFirst(),server.overworld()));
            context.waitFor(mc->BedDefenseHud.hardcoreRisk());
            context.runOnClient(mc->{if("살아남으세요".equals(title(mc)))
                throw new AssertionError("Manual bed pickup must NOT display raid destruction title");});
            context.waitTicks(6);context.takeScreenshot("r1-carried-yellow");
            world.getServer().runOnServer(server->{
                var p=server.getPlayerList().getPlayers().getFirst();var health=BedVitalData.get(server.overworld());health.destroy(p.getUUID(),"client_test");
            });
            context.waitTicks(20);context.takeScreenshot("r1-destroyed-red");
            world.getServer().runOnServer(ChallengeLifecycle::dragonKilled);
            context.waitFor(mc->!BedDefenseHud.hardcoreRisk());context.waitTicks(5);context.takeScreenshot("r1-victory-frozen-timer");
            // Separate new run: only a direct raid HP-zero hit is allowed to play the title.
            context.runOnClient(mc->mc.gui.hud.clearTitles());
            world.getServer().runOnServer(server->{
                var level=server.overworld();
                if(!ChallengeLifecycle.resetFinishedRun(server))
                    throw new AssertionError("Finished run must be restartable");
                var p=server.getPlayerList().getPlayers().getFirst();
                ChallengeData state=ChallengeData.get(level);
                if(!state.start(level.getGameTime(),ChallengeMenu.overworldClock(server)))
                    throw new AssertionError("Second run did not start");
                state.join(p.getUUID());
                BlockPos foot=p.blockPosition().offset(3,0,0);
                level.setBlock(foot.below(),Blocks.STONE.defaultBlockState(),3);
                level.setBlock(foot.south().below(),Blocks.STONE.defaultBlockState(),3);
                var bed=BuiltInRegistries.BLOCK.getValue(Identifier.parse("minecraft:white_bed"))
                        .defaultBlockState().setValue(BedBlock.FACING,Direction.SOUTH);
                level.setBlock(foot,bed.setValue(BedBlock.PART,BedPart.FOOT),2);
                level.setBlock(foot.south(),bed.setValue(BedBlock.PART,BedPart.HEAD),2);
                SpawnBedPrototype.onUse(p,level,InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(foot),Direction.UP,foot,false));
                SpawnBedPrototype.damage(server,p.getUUID(),100,"raid");
            });
            context.waitFor(mc->"살아남으세요".equals(title(mc)));
            context.takeScreenshot("r2-only-raid-broke-bed-warning");
        }
    }
    private static String title(net.minecraft.client.Minecraft mc){
        try{
            var field=net.minecraft.client.gui.Hud.class.getDeclaredField("title");field.setAccessible(true);
            var value=(net.minecraft.network.chat.Component)field.get(mc.gui.hud);
            return value==null?"":value.getString();
        }catch(ReflectiveOperationException e){throw new AssertionError("Cannot inspect the received title",e);}
    }
}
