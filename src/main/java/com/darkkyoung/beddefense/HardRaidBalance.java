package com.darkkyoung.beddefense;

import java.util.List;
import java.util.random.RandomGenerator;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * Proposed hard-mode playtest roster; sourced from the 2026-10-21 100-day balance sheet.
 * Counts include captains and babies. Easy/legacy-normal use RaidManager's original table.
 * Difficulty is server-owned and read from ChallengeOptionsData.
 */
public final class HardRaidBalance {
    public static final String BABY_ZOMBIE = "beddefense:baby_zombie";
    public static final int MAX_ROSTER = 150;
    // The agreed representative playtest values (day 30 deliberately uses 70).
    // Ratios interpolate between anchors; existing original hard totals remain the ceiling.
    private static final int[] NERF_ANCHOR_DAYS = {10,20,30,50,75,90};
    private static final int[] NERF_ANCHOR_BASIS_POINTS = {7500,7866,7777,8785,9568,10000};
    // One-player night totals, indexed by effective roster day (not calendar day).
    private static final int[] TOTALS = {
        12,17,22,27,32,38,43,48,53,58,60,62,63,65,67,69,70,72,73,75,
        76,78,80,81,82,84,86,87,88,90,91,92,93,94,94,95,96,97,98,99,
        100,101,101,102,103,104,105,105,106,107,107,108,108,109,109,109,110,110,111,111,
        111,112,112,113,113,113,114,114,115,115,115,116,116,116,116,117,117,117,118,118,
        118,119,119,119,120,120,120,120,121,121,121,122,122,123,123,123,124,124,125,125
    };
    /** All non-zombie regular types in their stable allocation order. */
    private static final List<String> ROLES = List.of(
        "minecraft:husk", "minecraft:spider", "minecraft:cave_spider",
        "minecraft:drowned", "minecraft:skeleton", "minecraft:stray",
        "minecraft:pillager", "minecraft:vindicator", "minecraft:creeper",
        "minecraft:witch", "minecraft:blaze", "minecraft:wither_skeleton"
    );

    // Percentages of (roster - captain). Fractions round down; zombies receive the remainder.
    // Effects/gear percentages apply only to eligible raiders, selected deterministically by index.
    public record Stage(int from, int to, int fire, int speed3, int speed2, int speed1,
                        int weapon, int armor, int water, int strength, int resistance,
                        int[] weights) { }

    private static final Stage[] STAGES = {
        new Stage(1,4, 35,0,0,30, 15,10,40,0,0,   new int[]{18,8,0,0,0,0,0,0,0,0,0,0}),
        new Stage(5,9, 55,0,10,50, 22,15,55,0,0,  new int[]{12,18,0,9,10,0,0,0,0,0,0,0}),
        new Stage(10,19,70,35,40,10, 30,20,70,5,0, new int[]{10,17,4,10,17,0,6,0,0,0,0,0}),
        new Stage(20,29,80,45,40,10, 40,30,80,10,0,new int[]{8,13,6,10,17,0,10,0,5,0,0,0}),
        new Stage(30,39,90,55,35,5, 50,40,85,15,10,new int[]{6,12,7,10,15,5,12,0,5,3,0,0}),
        new Stage(40,49,95,65,30,5, 60,50,90,20,15,new int[]{6,10,7,10,14,6,14,0,5,4,2,0}),
        new Stage(50,59,100,80,20,0, 65,55,95,25,15,new int[]{6,9,7,10,13,7,16,2,5,4,3,0}),
        new Stage(60,69,100,85,15,0, 70,60,100,30,20,new int[]{5,8,7,10,12,8,15,3,5,5,4,2}),
        new Stage(70,79,100,90,10,0, 72,65,100,35,20,new int[]{5,7,7,9,12,9,16,3,6,5,4,3}),
        new Stage(80,89,100,95,5,0, 75,70,100,40,25,new int[]{5,7,6,9,11,10,16,4,6,6,5,3}),
        new Stage(90,100,100,95,5,0, 80,75,100,40,30,new int[]{4,7,6,8,11,10,16,5,6,7,5,4})
    };

    private HardRaidBalance() { }

    public static boolean enabled(String difficulty) { return "어려움".equals(difficulty); }
    public static int totalForDay(int day) { return Math.min(MAX_ROSTER, TOTALS[Math.max(1,Math.min(100,day))-1]); }

    /** The top end of each night's hard-mode range remains the original alpha.7 roster. */
    public static int maxForDay(int day) {return totalForDay(day);}

    /** Base proposed nerf: 25% fewer early; anchor-interpolated toward original by day 90. */
    public static int recommendedForDay(int day) {
        int clamped=Math.max(1,Math.min(100,day));
        int basis=NERF_ANCHOR_BASIS_POINTS[0];
        for(int i=1;i<NERF_ANCHOR_DAYS.length;i++) {
            if(clamped<=NERF_ANCHOR_DAYS[i]){
                int left=NERF_ANCHOR_DAYS[i-1],right=NERF_ANCHOR_DAYS[i];
                int width=right-left, progress=Math.max(0,clamped-left);
                basis=(NERF_ANCHOR_BASIS_POINTS[i-1]*(width-progress)
                        +NERF_ANCHOR_BASIS_POINTS[i]*progress+width/2)/width;
                break;
            }
            basis=NERF_ANCHOR_BASIS_POINTS[i];
        }
        return (totalForDay(clamped)*basis+9999)/10000; // ceil, preserve promised anchors
    }
    /** Minimum = ceil(70% of the recommended nerfed number). */
    public static int minForDay(int day) {
        return (recommendedForDay(day)*7+9)/10;
    }
    /** Exactly one uniform inclusive draw per launched night; caller persists the result. */
    public static int rollForDay(int day,RandomGenerator random) {
        int low=minForDay(day), high=maxForDay(day);
        return random.nextInt(low,high+1);
    }
    public static Stage stage(int day) {
        int clamped = Math.max(1,Math.min(100,day));
        for (Stage stage : STAGES) if(clamped <= stage.to())return stage;
        return STAGES[STAGES.length-1];
    }
    public static String captainType(int day) {
        return day>=60 ? "minecraft:ravager" : day>=30 ? "minecraft:evoker" : "minecraft:vindicator";
    }
    public static int[] composition(int day) {
        return composition(day,totalForDay(day));
    }
    /** Allocate exactly the drawn number of mobs, including a single tenth-day captain. */
    public static int[] composition(int day,int total) {
        if(total<1||total>MAX_ROSTER)throw new IllegalArgumentException("Invalid hard raid count");
        int regular=total-(day%10==0?1:0);
        int[] roleCounts = new int[ROLES.size()+1]; // zero = ordinary + baby zombies
        int taken=0;
        int[] weights=stage(day).weights();
        for(int i=0;i<ROLES.size();i++){
            roleCounts[i+1]=regular*weights[i]/100;
            taken+=roleCounts[i+1];
        }
        roleCounts[0]=regular-taken;
        if(roleCounts[0]<0)throw new IllegalStateException("Hard roster ratios exceed 100%");
        return roleCounts;
    }
    public static int babyCount(int day) {return babyCount(day,totalForDay(day));}
    public static int babyCount(int day,int total) {
        if(day<10)return 0;
        int zombies=composition(day,total)[0];
        return Math.min(8,Math.min(zombies,Math.max(2,Math.round(zombies*(day>=60?.25f:.15f)))));
    }
    public static String mobForDay(int day,int index) {
        return mobForDay(day,index,totalForDay(day));
    }
    public static String mobForDay(int day,int index,int total) {
        if(total<1||total>MAX_ROSTER||index<0||index>=total)
            throw new IllegalArgumentException("Invalid hard roster index/count");
        if(day%10==0&&index==total-1)return captainType(day);
        int[] counts=composition(day,total);
        int zombieCount=counts[0];
        if(index<zombieCount)return index>=zombieCount-babyCount(day,total)?BABY_ZOMBIE:"minecraft:zombie";
        int cursor=zombieCount;
        for(int i=0;i<ROLES.size();i++){
            if(index<cursor+counts[i+1])return ROLES.get(i);
            cursor+=counts[i+1];
        }
        throw new IllegalStateException("Hard roster index outside allocated roles");
    }
    /** Stable pseudo-random selection across respawn/retry; does not depend on player joins. */
    static boolean chance(int day,int index,int salt,int percentage) {
        if(percentage<=0)return false;
        if(percentage>=100)return true;
        long x=(day+1L)*0x9E3779B97F4A7C15L
                ^(index+1L)*0xBF58476D1CE4E5B9L
                ^(salt+1L)*0x94D049BB133111EBL;
        x=(x^(x>>>30))*0xBF58476D1CE4E5B9L;
        x=(x^(x>>>27))*0x94D049BB133111EBL;
        return Long.remainderUnsigned(x^(x>>>31),10000L)<percentage*100L;
    }
    public static int speedAmplifier(String type,int day,int index) {
        // Baby zombies are naturally about as fast as a normal Speed-III zombie.
        // Double-applying the potion creates unfair, effectively unhittable mobs.
        if(BABY_ZOMBIE.equals(type) || !List.of("minecraft:zombie","minecraft:husk",
                "minecraft:spider","minecraft:cave_spider","minecraft:drowned",
                "minecraft:skeleton","minecraft:stray","minecraft:pillager",
                "minecraft:vindicator","minecraft:wither_skeleton").contains(type))return -1;
        Stage s=stage(day);
        if(chance(day,index,1,s.speed3()))return 2;
        if(chance(day,index,2,s.speed2()))return 1;
        return chance(day,index,3,s.speed1())?0:-1;
    }

    /** Called only on hard-mode raiders; vanilla behavior is unchanged on easy/legacy normal. */
    public static void configure(Mob mob,int day,int index,String type) {
        Stage s=stage(day);
        if(BABY_ZOMBIE.equals(type) && mob instanceof Zombie zombie)zombie.setBaby(true);
        if(chance(day,index,4,s.fire()))effect(mob,MobEffects.FIRE_RESISTANCE,0);
        int speed=speedAmplifier(type,day,index);
        if(speed>=0)effect(mob,MobEffects.SPEED,speed);
        // Dolphin's Grace can help living mobs swimming; the vanilla water-efficiency
        // attribute additionally reduces drag. Neither supplies missing AI pathfinding.
        var water=mob.getAttribute(Attributes.WATER_MOVEMENT_EFFICIENCY);
        if(water!=null)water.setBaseValue(s.water()/100.0);
        if(day>=5)effect(mob,MobEffects.DOLPHINS_GRACE,0);
        if(chance(day,index,5,s.resistance()))effect(mob,MobEffects.RESISTANCE,0);
        if(!type.equals("minecraft:ravager") && !type.equals("minecraft:evoker")
                && chance(day,index,6,s.strength()))effect(mob,MobEffects.STRENGTH,0);
        equipment(mob,day,index,type,s);
    }
    private static void effect(Mob mob, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect,int amplifier) {
        mob.addEffect(new MobEffectInstance(effect,-1,amplifier,false,false));
    }
    private static void equipment(Mob mob,int day,int index,String type,Stage s) {
        boolean hands=List.of("minecraft:zombie","minecraft:husk",BABY_ZOMBIE,
                "minecraft:drowned","minecraft:skeleton","minecraft:stray",
                "minecraft:pillager","minecraft:vindicator","minecraft:wither_skeleton").contains(type);
        if(!hands)return;
        boolean weaponRoll=chance(day,index,7,s.weapon());
        var held=mob.getItemBySlot(EquipmentSlot.MAINHAND);
        if(weaponRoll && held.isEmpty()){
            net.minecraft.world.item.Item item=day<5?Items.WOODEN_SWORD:
                    day<20?Items.STONE_SWORD:day<30?Items.IRON_AXE:
                    day>=70&&chance(day,index,8,day>=90?15:10)?Items.DIAMOND_SWORD:Items.IRON_SWORD;
            ItemStack weapon=new ItemStack(item);
            enchant(mob,weapon,day,false);
            mob.setItemSlot(EquipmentSlot.MAINHAND,weapon);
            mob.setDropChance(EquipmentSlot.MAINHAND,0.0f);
        }else if(weaponRoll && !held.isEmpty() && day>=30){
            enchant(mob,held,day,false);
            mob.setDropChance(EquipmentSlot.MAINHAND,0.0f);
        }
        if(chance(day,index,9,s.armor())&&mob.getItemBySlot(EquipmentSlot.CHEST).isEmpty()){
            net.minecraft.world.item.Item piece=day<10?Items.LEATHER_CHESTPLATE:
                    day<30?Items.CHAINMAIL_CHESTPLATE:
                    day>=70&&chance(day,index,10,day>=90?15:10)?Items.DIAMOND_CHESTPLATE:Items.IRON_CHESTPLATE;
            ItemStack armor=new ItemStack(piece);
            enchant(mob,armor,day,true);
            mob.setItemSlot(EquipmentSlot.CHEST,armor);
            mob.setDropChance(EquipmentSlot.CHEST,0.0f);
        }
    }
    private static void enchant(Mob mob,ItemStack stack,int day,boolean armor) {
        if(day<30||stack.isEnchanted())return;
        int lvl=day>=80?3:day>=50?2:1;
        if(!armor && stack.is(Items.BOW)){
            stack.enchant(mob.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                    .getOrThrow(Enchantments.POWER),lvl);
        }else if(!armor && stack.is(Items.CROSSBOW)){
            stack.enchant(mob.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                    .getOrThrow(Enchantments.QUICK_CHARGE),Math.min(3,lvl));
        }else {
            stack.enchant(mob.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                    .getOrThrow(armor?Enchantments.PROTECTION:Enchantments.SHARPNESS),lvl);
        }
    }
}
