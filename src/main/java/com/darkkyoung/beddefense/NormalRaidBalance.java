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
 * Normal v1 is an independent, versioned 100-day profile between Easy and Hard.
 * Uses the agreed representative day ranges and interpolates all remaining days.
 * All totals INCLUDE the single captain on each tenth effective roster day.
 */
public final class NormalRaidBalance {
    public static final int MAX_ROSTER = HardRaidBalance.MAX_ROSTER;
    private static final int[] ANCHOR_DAYS = {1,2,3,5,10,20,30,50,75,90,100};
    private static final int[] ANCHOR_MIN = {6,9,11,14,25,34,41,53,62,75,84};
    private static final int[] ANCHOR_MAX = {8,11,13,18,31,42,51,65,76,91,102};
    private static final List<String> ROLES = List.of(
            "minecraft:husk","minecraft:spider","minecraft:cave_spider",
            "minecraft:drowned","minecraft:skeleton","minecraft:stray",
            "minecraft:pillager","minecraft:vindicator","minecraft:creeper",
            "minecraft:witch","minecraft:blaze","minecraft:wither_skeleton");

    // Percentages refer to eligible monsters. Each slot is selected by a
    // stable hash of effective roster day, slot and its own salt.
    public record Stage(int from,int to,int fire,int speed3,int speed2,int speed1,
                        int weapon,int armor,int water,int strength,int resistance,
                        int[] weights) { }

    private static final Stage[] STAGES = {
        new Stage(1,4,    10,0,0,10,    5,5,0,0,0,
                new int[]{12,10,0,0,0,0,0,0,0,0,0,0}),
        new Stage(5,9,    25,0,5,20,   12,10,15,0,0,
                new int[]{10,17,0,0,12,0,0,0,0,0,0,0}),
        new Stage(10,19,  35,0,15,35,  20,15,30,0,0,
                new int[]{9,15,0,7,16,0,6,0,0,0,0,0}),
        new Stage(20,24,  45,0,20,45,  28,20,40,0,0,
                new int[]{8,13,5,8,16,3,6,0,4,0,0,0}),
        new Stage(25,29,  50,15,25,35, 30,22,45,0,0,
                new int[]{8,13,5,8,16,3,6,0,4,0,0,0}),
        new Stage(30,39,  55,25,35,25, 33,28,50,5,0,
                new int[]{7,12,6,9,15,5,9,0,5,3,0,0}),
        new Stage(40,49,  65,45,40,10, 38,33,60,10,0,
                new int[]{7,11,6,9,13,6,9,2,5,4,2,0}),
        new Stage(50,50,  75,60,35,5,  45,40,70,15,5,
                new int[]{6,11,6,9,12,7,10,3,6,5,2,0}),
        // Vanilla Easy grants Speed III to all eligible raiders from day 51.
        // Normal matches that floor, then exceeds Easy in composition, gear and numbers.
        // Existing Hard V2 is unchanged, including its own 80% roll on day 51.
        new Stage(51,59,  75,100,0,0,  45,40,70,15,5,
                new int[]{6,11,6,9,12,7,10,3,6,5,2,0}),
        new Stage(60,69,  80,100,0,0,  48,43,75,15,8,
                new int[]{6,9,6,10,12,7,11,3,5,5,3,2}),
        new Stage(70,79,  85,100,0,0,  50,47,80,18,10,
                new int[]{6,8,7,9,11,9,12,4,6,6,3,3}),
        new Stage(80,89,  90,100,0,0,  55,50,85,20,12,
                new int[]{5,7,7,9,11,9,12,4,6,6,5,3}),
        new Stage(90,100, 95,100,0,0,  60,55,85,22,15,
                new int[]{5,7,6,9,10,10,13,5,6,7,5,3})
    };

    private NormalRaidBalance(){}

    private static int interpolate(int day,int[] anchors){
        int d=Math.max(1,Math.min(100,day));
        for(int i=1;i<ANCHOR_DAYS.length;i++){
            if(d<=ANCHOR_DAYS[i]){
                int start=ANCHOR_DAYS[i-1],width=ANCHOR_DAYS[i]-start;
                int a=anchors[i-1],b=anchors[i];
                return a+(int)Math.round((b-a)*(d-start)/(double)width);
            }
        }
        return anchors[anchors.length-1];
    }
    public static int minForDay(int day){return interpolate(day,ANCHOR_MIN);}
    public static int maxForDay(int day){return Math.min(MAX_ROSTER,interpolate(day,ANCHOR_MAX));}
    public static int rollForDay(int day,RandomGenerator random){
        return random.nextInt(minForDay(day),maxForDay(day)+1);
    }
    public static Stage stage(int day){
        int d=Math.max(1,Math.min(100,day));
        for(Stage stage:STAGES)if(d<=stage.to())return stage;
        return STAGES[STAGES.length-1];
    }
    public static int[] composition(int day,int total){
        if(total<1||total>MAX_ROSTER)throw new IllegalArgumentException("Invalid Normal roster size");
        int regular=total-(day%10==0?1:0);
        int[] counts=new int[ROLES.size()+1];
        int taken=0;
        int[] weights=stage(day).weights();
        for(int i=0;i<ROLES.size();i++){
            counts[i+1]=regular*weights[i]/100;
            taken+=counts[i+1];
        }
        if(taken>regular)throw new IllegalStateException("Normal roster percentages exceed 100%");
        counts[0]=regular-taken;
        return counts;
    }
    public static int babyCount(int day,int total){
        if(day<20)return 0;
        int zombies=composition(day,total)[0];
        return Math.min(3,Math.min(zombies,Math.max(1,Math.round(zombies*0.07f))));
    }
    public static String mobForDay(int day,int index,int total){
        if(index<0||index>=total)throw new IllegalArgumentException("Invalid Normal roster index");
        if(day%10==0&&index==total-1)return HardRaidBalance.captainType(day);
        int[] counts=composition(day,total);
        int zombies=counts[0];
        if(index<zombies)return index>=zombies-babyCount(day,total)
                ?HardRaidBalance.BABY_ZOMBIE:"minecraft:zombie";
        int cursor=zombies;
        for(int i=0;i<ROLES.size();i++){
            if(index<cursor+counts[i+1])return ROLES.get(i);
            cursor+=counts[i+1];
        }
        throw new IllegalStateException("Normal roster index outside allocation");
    }
    public static int speedAmplifier(String type,int day,int index){
        if(HardRaidBalance.BABY_ZOMBIE.equals(type)||!List.of(
                "minecraft:zombie","minecraft:husk","minecraft:spider",
                "minecraft:cave_spider","minecraft:drowned","minecraft:skeleton",
                "minecraft:stray","minecraft:pillager","minecraft:vindicator",
                "minecraft:wither_skeleton").contains(type))return -1;
        Stage s=stage(day);
        if(HardRaidBalance.chance(day,index,101,s.speed3()))return 2;
        if(HardRaidBalance.chance(day,index,102,s.speed2()))return 1;
        return HardRaidBalance.chance(day,index,103,s.speed1())?0:-1;
    }
    /** Server-applied Normal V1 effects/gear, deliberately below Hard's stages. */
    public static void configure(Mob mob,int day,int index,String type){
        Stage s=stage(day);
        if(HardRaidBalance.BABY_ZOMBIE.equals(type)&&mob instanceof Zombie zombie)
            zombie.setBaby(true);
        if(HardRaidBalance.chance(day,index,104,s.fire()))effect(mob,MobEffects.FIRE_RESISTANCE,0);
        int speed=speedAmplifier(type,day,index);
        if(speed>=0)effect(mob,MobEffects.SPEED,speed);
        var water=mob.getAttribute(Attributes.WATER_MOVEMENT_EFFICIENCY);
        if(water!=null)water.setBaseValue(s.water()/100.0);
        if(day>=10)effect(mob,MobEffects.DOLPHINS_GRACE,0);
        if(HardRaidBalance.chance(day,index,105,s.resistance()))
            effect(mob,MobEffects.RESISTANCE,0);
        if(!type.equals("minecraft:ravager")&&!type.equals("minecraft:evoker")
                &&HardRaidBalance.chance(day,index,106,s.strength()))
            effect(mob,MobEffects.STRENGTH,0);
        equip(mob,day,index,type,s);
    }
    private static void effect(Mob mob,net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect,int amplifier){
        mob.addEffect(new MobEffectInstance(effect,-1,amplifier,false,false));
    }
    private static void equip(Mob mob,int day,int index,String type,Stage s){
        boolean hands=List.of("minecraft:zombie","minecraft:husk",HardRaidBalance.BABY_ZOMBIE,
                "minecraft:drowned","minecraft:skeleton","minecraft:stray",
                "minecraft:pillager","minecraft:vindicator","minecraft:wither_skeleton").contains(type);
        if(!hands)return;
        boolean weapon=HardRaidBalance.chance(day,index,107,s.weapon());
        ItemStack held=mob.getItemBySlot(EquipmentSlot.MAINHAND);
        if(weapon&&held.isEmpty()){
            var item=day<10?Items.WOODEN_SWORD:day<30?Items.STONE_SWORD:
                    day<60?Items.IRON_SWORD:Items.IRON_AXE;
            ItemStack sword=new ItemStack(item);
            enchant(mob,sword,day,false);
            mob.setItemSlot(EquipmentSlot.MAINHAND,sword);
            mob.setDropChance(EquipmentSlot.MAINHAND,0.0f);
        }else if(weapon&&day>=40&&!held.isEmpty()){
            enchant(mob,held,day,false);
            mob.setDropChance(EquipmentSlot.MAINHAND,0.0f);
        }
        if(HardRaidBalance.chance(day,index,108,s.armor())&&mob.getItemBySlot(EquipmentSlot.CHEST).isEmpty()){
            var armor=day<20?Items.LEATHER_CHESTPLATE:day<40?Items.CHAINMAIL_CHESTPLATE:
                    day>=90&&HardRaidBalance.chance(day,index,109,5)
                        ?Items.DIAMOND_CHESTPLATE:Items.IRON_CHESTPLATE;
            ItemStack chest=new ItemStack(armor);
            enchant(mob,chest,day,true);
            mob.setItemSlot(EquipmentSlot.CHEST,chest);
            mob.setDropChance(EquipmentSlot.CHEST,0.0f);
        }
    }
    private static void enchant(Mob mob,ItemStack item,int day,boolean armor){
        if(day<40||item.isEnchanted())return;
        int level=day>=90?3:day>=65?2:1;
        var registry=mob.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        if(!armor&&item.is(Items.BOW))
            item.enchant(registry.getOrThrow(Enchantments.POWER),level);
        else if(!armor&&item.is(Items.CROSSBOW))
            item.enchant(registry.getOrThrow(Enchantments.QUICK_CHARGE),Math.min(3,level));
        else item.enchant(registry.getOrThrow(armor?Enchantments.PROTECTION:Enchantments.SHARPNESS),level);
    }
}
