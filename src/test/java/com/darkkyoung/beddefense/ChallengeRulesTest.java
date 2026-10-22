package com.darkkyoung.beddefense;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChallengeRulesTest {
    @Test void onlyFirstWelcomeGetsTemporaryFullChatHeight(){
        try {
            com.darkkyoung.beddefense.client.WelcomeChatDisplay.show(false);
            assertEquals(90,com.darkkyoung.beddefense.client.WelcomeChatDisplay.height(90,540,1.0));
            com.darkkyoung.beddefense.client.WelcomeChatDisplay.show(true);
            assertEquals(240,com.darkkyoung.beddefense.client.WelcomeChatDisplay.height(90,540,1.0));
            assertEquals(240,com.darkkyoung.beddefense.client.WelcomeChatDisplay.height(90,540,0.75));
            assertEquals(120,com.darkkyoung.beddefense.client.WelcomeChatDisplay.height(90,190,1.0));
            assertEquals(90,com.darkkyoung.beddefense.client.WelcomeChatDisplay.height(90,190,Double.NaN));
            assertTrue(new com.darkkyoung.beddefense.network.MenuUiPayload(false,true).welcome());
            assertFalse(new com.darkkyoung.beddefense.network.MenuUiPayload(false).welcome(),
                    "All setup and countdown packets must restore vanilla chat height");
        } finally {
            com.darkkyoung.beddefense.client.WelcomeChatDisplay.show(false);
        }
    }
    @Test void welcomeIntroducesChallengeBeforeStartButton(){
        var lines=ChallengeMenu.welcomeMessages();
        var visible=lines.stream().map(net.minecraft.network.chat.Component::getString)
                .map(value->value.replaceAll("§[0-9a-fk-or]","")).toList();
        int greeting=visible.indexOf("베드 디펜스에 오신 것을 환영합니다!");
        int intro=visible.indexOf("낮에는 탐험하고, 밤에는 몬스터의 습격으로부터 침대를 지키는 100일 생존 챌린지입니다.");
        int start=-1;
        for(int i=0;i<visible.size();i++)if(visible.get(i).contains("[게임 시작]")){start=i;break;}
        assertTrue(greeting>=0 && intro>greeting && start>intro,
                "welcome must display greeting, description and only then the clickable start");
        assertTrue(lines.get(start).getStyle().isBold());
        assertInstanceOf(net.minecraft.network.chat.ClickEvent.RunCommand.class,
                lines.get(start).getStyle().getClickEvent());
    }
    @Test void confirmActionUsesBoldPaleLimeNotInactiveGray(){
        var button=ChallengeSetupWizard.confirmStartButton();
        assertEquals(net.minecraft.network.chat.TextColor.fromRgb(0xA8FF73),
                button.getStyle().getColor());
        assertTrue(button.getStyle().isBold());
        assertInstanceOf(net.minecraft.network.chat.ClickEvent.RunCommand.class,
                button.getStyle().getClickEvent());
        assertTrue(button.getString().contains("[설정 완료 · 시작]"));
    }

    @Test void hardRandomBoundsHonorAgreedPlaytestExamples(){
        int[][] expected={{1,7,9,12},{2,10,13,17},{5,17,24,32},{10,31,44,58},
                {20,42,59,75},{30,49,70,90},{50,66,94,107},{75,78,111,116},
                {90,85,121,121},{100,88,125,125}};
        for(int[] row:expected){
            assertEquals(row[1],HardRaidBalance.minForDay(row[0]),"min day "+row[0]);
            assertEquals(row[2],HardRaidBalance.recommendedForDay(row[0]),"proposal day "+row[0]);
            assertEquals(row[3],HardRaidBalance.maxForDay(row[0]),"max day "+row[0]);
        }
        var seeded=new java.util.Random(20261021);
        for(int day=1;day<=100;day++){
            final int d=day;
            int min=HardRaidBalance.minForDay(day),max=HardRaidBalance.maxForDay(day);
            assertTrue(min>0&&min<=max&&max<=HardRaidBalance.MAX_ROSTER);
            for(int i=0;i<30;i++){
                int draw=HardRaidBalance.rollForDay(day,seeded);
                assertTrue(draw>=min&&draw<=max,"out-of-bounds hard draw on day "+day);
            }
            for(int selected:new int[]{min,(min+max)/2,max}){
                int[] counts=HardRaidBalance.composition(day,selected);
                assertEquals(selected,java.util.Arrays.stream(counts).sum()+(day%10==0?1:0));
                int babies=0,captains=0;
                for(int index=0;index<selected;index++){
                    String mob=HardRaidBalance.mobForDay(day,index,selected);
                    assertNotNull(mob);
                    if(HardRaidBalance.BABY_ZOMBIE.equals(mob))babies++;
                    if(day%10==0&&HardRaidBalance.captainType(day).equals(mob))captains++;
                }
                assertEquals(day%10==0?1:0,captains,"exact captain count day "+day);
                assertEquals(HardRaidBalance.babyCount(day,selected),babies,"baby roster day "+day);
            }
        }
    }

    @Test void oneRandomDrawPersistsThroughRaidSaveAndCannotReroll(){
        int day=10,selected=HardRaidBalance.minForDay(day);
        var owner1=UUID.randomUUID().toString();
        var owner2=UUID.randomUUID().toString();
        var batches=List.of(new RaidStateData.Pending("minecraft:overworld",owner1,day,day,0,selected),
                new RaidStateData.Pending("minecraft:overworld",owner2,day,day,0,selected));
        RaidStateData state=new RaidStateData();
        state.launch(day,batches,selected);
        assertEquals(day,state.lastRandomWave());
        assertEquals(selected,state.lastRandomRoster());
        assertEquals(selected*2,state.remaining());
        state=RaidStateData.CODEC.parse(JsonOps.INSTANCE,
                RaidStateData.CODEC.encodeStart(JsonOps.INSTANCE,state).getOrThrow()).getOrThrow();
        assertEquals(day,state.lastRandomWave());
        assertEquals(selected,state.lastRandomRoster());
        assertEquals(selected*2,state.remaining());
        RaidStateData reloaded=state;
        assertThrows(IllegalStateException.class,()->reloaded.launch(day,batches,selected));
        RaidStateData.Pending first=reloaded.pending().getFirst();
        UUID mob=UUID.randomUUID();
        reloaded.spawned(first,mob);
        assertEquals(selected*2,reloaded.remaining());
        assertEquals(selected,reloaded.pending().stream().filter(p->p.owner().equals(owner1))
                .mapToInt(p->p.next()+p.count()).findFirst().orElseThrow());
        assertEquals(HardRaidBalance.captainType(day),
                HardRaidBalance.mobForDay(day,selected-1,selected));
        reloaded.resetForNewRun();
        assertEquals(-1,reloaded.lastRandomRoster());
        assertEquals(-1,reloaded.lastRandomWave());
    }

    @Test void hardRosterAndBabyZombieSlots(){
        assertEquals(12,HardRaidBalance.totalForDay(1));
        assertEquals(58,HardRaidBalance.totalForDay(10));
        assertEquals(107,HardRaidBalance.totalForDay(50));
        assertEquals(125,HardRaidBalance.totalForDay(100));
        for(int day=1;day<=100;day++){
            int total=HardRaidBalance.totalForDay(day);
            assertTrue(total<=HardRaidBalance.MAX_ROSTER);
            int allocated=java.util.Arrays.stream(HardRaidBalance.composition(day)).sum()+(day%10==0?1:0);
            assertEquals(total,allocated,"hard roster partition day "+day);
            for(int n=0;n<total;n++)assertNotNull(HardRaidBalance.mobForDay(day,n));
        }
        assertEquals(0,HardRaidBalance.babyCount(9));
        assertTrue(HardRaidBalance.babyCount(10)>=2);
        assertTrue(java.util.stream.IntStream.range(0,58)
                .mapToObj(i->HardRaidBalance.mobForDay(10,i))
                .anyMatch(HardRaidBalance.BABY_ZOMBIE::equals));
        assertEquals("minecraft:vindicator",HardRaidBalance.mobForDay(10,57));
        assertEquals("minecraft:ravager",HardRaidBalance.mobForDay(100,124));
    }
    @Test void hardSpeedIIIStartsOnDay10WithoutOverbuffingBabies(){
        for(int day=1;day<10;day++)
            for(int i=0;i<100;i++)assertNotEquals(2,HardRaidBalance.speedAmplifier("minecraft:zombie",day,i));
        assertTrue(java.util.stream.IntStream.range(0,58)
                .anyMatch(i->HardRaidBalance.speedAmplifier("minecraft:zombie",10,i)==2));
        for(int day:new int[]{10,50,100}){
            for(int i=0;i<HardRaidBalance.totalForDay(day);i++){
                assertEquals(-1,HardRaidBalance.speedAmplifier(HardRaidBalance.BABY_ZOMBIE,day,i));
                assertEquals(-1,HardRaidBalance.speedAmplifier("minecraft:creeper",day,i));
            }
        }
    }
    @Test void preUpdateChallengeNeverAccidentallyEnablesHardMode(){
        var legacy=ChallengeData.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"start_game_tick\":100,\"start_day_index\":1}")).getOrThrow();
        assertEquals(ChallengeData.PROFILE_LEGACY,legacy.raidProfile());
        var hard=new ChallengeData();
        assertTrue(hard.start(100,0,ChallengeData.PROFILE_HARD));
        hard=ChallengeData.CODEC.parse(JsonOps.INSTANCE,
                ChallengeData.CODEC.encodeStart(JsonOps.INSTANCE,hard).getOrThrow()).getOrThrow();
        assertEquals(ChallengeData.PROFILE_HARD,hard.raidProfile());
        assertTrue(hard.hardRaidProfile());
        assertFalse(hard.randomizedHardRaidProfile(),"running alpha.7 hard must remain legacy hard");
        assertTrue(hard.finish(ChallengeData.WON,120,"test"));
        assertTrue(hard.resetFinishedRun());
        assertEquals(ChallengeData.PROFILE_LEGACY,hard.raidProfile());
        assertTrue(hard.start(140,0,ChallengeData.PROFILE_HARD_V2));
        hard=ChallengeData.CODEC.parse(JsonOps.INSTANCE,
                ChallengeData.CODEC.encodeStart(JsonOps.INSTANCE,hard).getOrThrow()).getOrThrow();
        assertTrue(hard.randomizedHardRaidProfile());
        assertEquals(ChallengeData.PROFILE_HARD_V2,hard.raidProfile());
    }
    @Test void hardMorningSettlementDoesNotPenalizeUnspawnedQueue(){
        var state=new RaidStateData();
        var owner=UUID.randomUUID().toString();
        state.launch(1,java.util.List.of(new RaidStateData.Pending("minecraft:overworld",owner,1,1,0,12)));
        UUID id=UUID.randomUUID();
        state.track(id,new RaidStateData.Raider("minecraft:overworld",owner,1,1));
        var penalty=state.settleMorning(false);
        assertEquals(1,penalty.get(owner));
        assertEquals(0,state.remaining());
        assertTrue(state.pendingCleanup(id));
    }
    @Test void rosterProgressionAndCap(){
        assertEquals(List.of(1,3,5,7,9),java.util.stream.LongStream.rangeClosed(1,5).mapToObj(d->ChallengeBalance.rosterDay(d,2)).toList());
        assertEquals(100,ChallengeBalance.rosterDay(100,100));assertEquals(100,ChallengeBalance.rosterDay(101,1));
        assertEquals(5,RaidManager.totalForDay(1));assertEquals(80,RaidManager.totalForDay(100));
        for(int i=0;i<5;i++)assertEquals("minecraft:zombie",RaidManager.mobForDay(1,i,5));
    }
    @Test void speedThreeBoundariesAndSpecialExclusions(){
        assertEquals(2,RaidManager.speedAmplifier("minecraft:zombie",41,0));
        assertNotEquals(2,RaidManager.speedAmplifier("minecraft:zombie",40,0));
        for(String type:List.of("zombie","spider","skeleton","pillager","vindicator","wither_skeleton"))
            for(int i=0;i<20;i++)assertEquals(2,RaidManager.speedAmplifier("minecraft:"+type,51,i));
        for(String type:List.of("creeper","blaze","witch","ravager"))assertEquals(-1,RaidManager.speedAmplifier("minecraft:"+type,100,0));
    }
    @Test void targetsCompareEachMobsDistances(){
        assertTrue(ChallengeBalance.preferBed(100,9));assertFalse(ChallengeBalance.preferBed(4,9));
        assertFalse(ChallengeBalance.preferBed(9,9));assertFalse(ChallengeBalance.preferBed(25,Double.POSITIVE_INFINITY));
    }
    @Test void bedTokenSurvivesReloadButCannotBeReplayed(){
        UUID owner=UUID.randomUUID();BedVitalData data=new BedVitalData();data.register(owner);data.damage(owner,37);
        assertTrue(data.unplace(owner));String token=data.token(owner);
        data=BedVitalData.CODEC.parse(JsonOps.INSTANCE,BedVitalData.CODEC.encodeStart(JsonOps.INSTANCE,data).getOrThrow()).getOrThrow();
        assertEquals(63,data.hp(owner));assertTrue(data.validToken(owner,token));assertFalse(data.validToken(UUID.randomUUID(),token));
        assertFalse(data.place(owner,"ordinary-bed"));assertTrue(data.place(owner,token));assertFalse(data.place(owner,token));
        assertTrue(data.unplace(owner));assertNotEquals(token,data.token(owner));assertFalse(data.place(owner,token));
    }
    @Test void destroyedBedCannotRegisterRepairOrReinstall(){
        UUID id=UUID.randomUUID();BedVitalData data=new BedVitalData();data.register(id);data.damage(id,100,"dimension_death");
        data.register(id);assertEquals(2,data.state(id));assertEquals(0,data.hp(id));assertEquals(0,data.repair(id,100));assertFalse(data.unplace(id));assertFalse(data.place(id,""));
    }
    @Test void repairIsCappedAndPickupKeepsHealth(){
        UUID id=UUID.randomUUID();BedVitalData data=new BedVitalData();data.register(id);data.damage(id,7);
        assertEquals(7,data.repair(id,10));assertEquals(100,data.hp(id));data.unplace(id);assertEquals(100,data.hp(id));assertEquals(0,data.repair(id,10));
    }
    @Test void legacyChallengeAndVitalsMigrateWithoutReset(){
        ChallengeData data=ChallengeData.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseString("{\"start_game_tick\":120,\"start_day_index\":4}")).getOrThrow();
        assertTrue(data.running());assertEquals(200,data.elapsedTicks(320));assertEquals(2,data.day(5*24000));
        UUID id=UUID.randomUUID();String old="{\"hp\":{\""+id+"\":42},\"states\":{\""+id+"\":1}}";
        BedVitalData bed=BedVitalData.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseString(old)).getOrThrow();assertEquals(42,bed.hp(id));
    }
    @Test void lastRespawnAndEliminationPersist(){
        UUID id=UUID.randomUUID();ChallengeData data=new ChallengeData();data.start(100,0);data.join(id);data.playerState(id,ChallengeData.FINAL_RESPAWN);
        data=ChallengeData.CODEC.parse(JsonOps.INSTANCE,ChallengeData.CODEC.encodeStart(JsonOps.INSTANCE,data).getOrThrow()).getOrThrow();
        assertEquals(ChallengeData.FINAL_RESPAWN,data.playerState(id));data.playerState(id,ChallengeData.ALIVE);data.join(id);assertEquals(ChallengeData.ALIVE,data.playerState(id));
        data.playerState(id,ChallengeData.ELIMINATED);data.join(id);assertTrue(data.allEliminated());assertFalse(data.participating(id));
    }
    @Test void day100EndsOnlyAt101AndTimerFreezes(){
        ChallengeData data=new ChallengeData();assertTrue(data.start(100,12542));assertFalse(data.start(101,0));
        assertEquals(100,data.day(99*24000+23999));assertEquals(101,data.day(100*24000));
        data.observeDay(100*24000);assertTrue(data.finish(ChallengeData.LOST,2100,"deadline"));assertFalse(data.finish(ChallengeData.WON,2200,"dragon"));
        assertEquals(2000,data.elapsedTicks(999999));assertEquals(101,data.day(0));
    }
    @Test void completedChallengeRestartsWithNewDayOneButActiveRunCannotReset(){
        ChallengeData state=new ChallengeData();
        UUID owner=UUID.randomUUID();
        state.start(100,0);state.join(owner);
        assertFalse(state.resetFinishedRun()); // never reset an ongoing run
        state.playerState(owner,ChallengeData.ELIMINATED);
        assertTrue(state.finish(ChallengeData.LOST,400,"test"));
        assertTrue(state.canRestart());
        assertTrue(state.resetFinishedRun());
        assertFalse(state.isStarted());
        assertEquals(ChallengeData.READY,state.outcome());
        assertEquals(-1,state.elapsedTicks(999));
        assertFalse(state.allEliminated());
        assertEquals("",state.reason());
        assertFalse(state.resetFinishedRun()); // cannot reset READY twice
        assertTrue(state.start(2000,3*24000));
        assertEquals(1,state.day(3*24000));
        assertEquals(0,state.elapsedTicks(2000));
        state.join(owner);assertTrue(state.participating(owner));
    }
    @Test void newRunClearsBedAndRaidLedgerButRetainsUnloadedCleanup(){
        UUID owner=UUID.randomUUID(),oldRaider=UUID.randomUUID();
        BedVitalData bed=new BedVitalData();
        bed.register(owner);bed.damage(owner,75);assertEquals(25,bed.hp(owner));
        bed.resetForNewRun();
        assertEquals(0,bed.state(owner));
        assertEquals(0,bed.hp(owner));
        bed.register(owner);
        assertEquals(100,bed.hp(owner));
        RaidStateData raid=new RaidStateData();
        raid.launch(43,java.util.List.of(new RaidStateData.Pending(
                "minecraft:overworld",owner.toString(),43,43,0,2)));
        raid.track(oldRaider,new RaidStateData.Raider(
                "minecraft:overworld",owner.toString(),43,43));
        raid.resetForNewRun();
        assertFalse(raid.active());
        assertEquals(0,raid.lastRaidDay());
        assertEquals(0,raid.remaining());
        assertTrue(raid.pendingCleanup(oldRaider));
        raid.launch(1,java.util.List.of(new RaidStateData.Pending(
                "minecraft:overworld",owner.toString(),1,1,0,5)));
        assertEquals(5,raid.remaining());
        assertEquals(1,raid.lastRaidDay());
    }
    @Test void onlyDirectRaidBedDestructionShowsSurvivalTitle(){
        assertTrue(SpawnBedPrototype.isRaiderBedKill("raid"));
        for(String cause:java.util.List.of("owner_pickup","sunrise","dimension_death",
                "external_block_removal","debug","client_test"))
            assertFalse(SpawnBedPrototype.isRaiderBedKill(cause));
        assertFalse(SpawnBedPrototype.isRaiderBedKill(null));
        assertEquals(1,ChallengeBalance.REPAIR_HP_PER_GEM);
    }

    @Test void clockRewindCannotExtendDeadline(){ChallengeData d=new ChallengeData();d.start(0,0);d.observeDay(40*24000);assertEquals(41,d.day(0));}
    @Test void morningLedgerCountsUnloadedAndQueuedByDimensionAndOwner(){
        String a=UUID.randomUUID().toString(),b=UUID.randomUUID().toString();UUID unloaded=UUID.randomUUID(),nether=UUID.randomUUID();
        RaidStateData d=new RaidStateData();d.launch(1,List.of(new RaidStateData.Pending("minecraft:overworld",a,1,1,0,3),new RaidStateData.Pending("minecraft:the_end",b,1,1,0,4)));
        d.track(unloaded,new RaidStateData.Raider("minecraft:overworld",a,1,1));d.track(nether,new RaidStateData.Raider("minecraft:the_nether",b,1,1));
        d=RaidStateData.CODEC.parse(JsonOps.INSTANCE,RaidStateData.CODEC.encodeStart(JsonOps.INSTANCE,d).getOrThrow()).getOrThrow();
        assertEquals(Map.of(a,4),d.settleMorning());assertTrue(d.pendingCleanup(unloaded));assertTrue(d.isRaider(nether));assertEquals(5,d.remaining());
        d.remove(unloaded);assertTrue(d.pendingCleanup(unloaded));assertTrue(d.settleMorning().isEmpty());
        d.launch(2,List.of(new RaidStateData.Pending("minecraft:the_end",b,3,2,0,6)));assertEquals(11,d.remaining());
    }
    @Test void spawnRetryAndDuplicateLaunchNeverLoseOrDuplicateSoldiers(){
        RaidStateData d=new RaidStateData();var batch=new RaidStateData.Pending("minecraft:overworld",UUID.randomUUID().toString(),1,1,0,5);d.launch(1,List.of(batch));
        assertEquals(5,d.remaining());UUID id=UUID.randomUUID();d.spawned(batch,id);assertEquals(5,d.remaining());
        assertThrows(IllegalStateException.class,()->d.spawned(batch,UUID.randomUUID()));assertThrows(IllegalStateException.class,()->d.launch(1,List.of()));
        d.remove(id);assertEquals(4,d.remaining());assertEquals(4,d.settleMorning().values().iterator().next());
    }
}
