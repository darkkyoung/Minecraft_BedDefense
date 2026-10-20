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

/** States: 0 unregistered, 1 placed, 2 permanently destroyed, 3 carried. */
public final class BedVitalData extends SavedData {
    public static final int MAX_HEALTH=100;
    public static final Codec<BedVitalData> CODEC=RecordCodecBuilder.create(i->i.group(
        Codec.unboundedMap(Codec.STRING,Codec.INT).fieldOf("hp").forGetter(d->Map.copyOf(d.hp)),
        Codec.unboundedMap(Codec.STRING,Codec.INT).fieldOf("states").forGetter(d->Map.copyOf(d.states)),
        Codec.unboundedMap(Codec.STRING,Codec.STRING).optionalFieldOf("tokens",Map.of()).forGetter(d->Map.copyOf(d.tokens)),
        Codec.unboundedMap(Codec.STRING,Codec.STRING).optionalFieldOf("causes",Map.of()).forGetter(d->Map.copyOf(d.causes))
    ).apply(i,BedVitalData::new));
    static final SavedDataType<BedVitalData> TYPE=new SavedDataType<>(Identifier.fromNamespaceAndPath("beddefense","bed_vitals"),BedVitalData::new,CODEC,null);
    private final Map<String,Integer> hp,states;
    private final Map<String,String> tokens,causes;
    public BedVitalData(){this(Map.of(),Map.of(),Map.of(),Map.of());}
    private BedVitalData(Map<String,Integer> hp,Map<String,Integer> states,Map<String,String> tokens,Map<String,String> causes){
        this.hp=new HashMap<>(hp);this.states=new HashMap<>(states);this.tokens=new HashMap<>(tokens);this.causes=new HashMap<>(causes);
    }
    public static BedVitalData get(ServerLevel world){return world.getDataStorage().computeIfAbsent(TYPE);}
    public int state(UUID owner){return states.getOrDefault(owner.toString(),0);}
    public int hp(UUID owner){return hp.getOrDefault(owner.toString(),0);}
    public String cause(UUID owner){return causes.getOrDefault(owner.toString(),"");}
    public void register(UUID owner){if(state(owner)!=0)return;states.put(owner.toString(),1);hp.put(owner.toString(),100);setDirty();}
    public boolean unplace(UUID owner){
        if(state(owner)!=1)return false;
        states.put(owner.toString(),3);tokens.put(owner.toString(),UUID.randomUUID().toString());causes.put(owner.toString(),"owner_pickup");setDirty();return true;
    }
    public String token(UUID owner){return tokens.getOrDefault(owner.toString(),"");}
    public boolean validToken(UUID owner,String token){return state(owner)==3&&!token.isEmpty()&&token.equals(token(owner));}
    public boolean place(UUID owner,String token){
        if(!validToken(owner,token))return false;
        states.put(owner.toString(),1);tokens.remove(owner.toString());causes.remove(owner.toString());setDirty();return true;
    }
    public int repair(UUID owner,int amount){
        if(state(owner)!=1||amount<=0)return 0;
        int restored=Math.min(amount,100-hp(owner));hp.put(owner.toString(),hp(owner)+restored);setDirty();return restored;
    }
    public int damage(UUID owner,int amount){return damage(owner,amount,"raid");}
    public int damage(UUID owner,int amount,String cause){
        if(state(owner)!=1||amount<=0)return hp(owner);
        int next=Math.max(0,hp(owner)-amount);hp.put(owner.toString(),next);
        if(next==0){states.put(owner.toString(),2);tokens.remove(owner.toString());causes.put(owner.toString(),cause);}setDirty();return next;
    }
    public boolean destroy(UUID owner){return destroy(owner,"external_block_removal");}
    public boolean destroy(UUID owner,String cause){
        if(state(owner)==0||state(owner)==2)return false;
        states.put(owner.toString(),2);hp.put(owner.toString(),0);tokens.remove(owner.toString());causes.put(owner.toString(),cause);setDirty();return true;
    }
    public void debugHealth(UUID owner,int value){if(state(owner)!=1)return;if(value<=0)destroy(owner,"debug");else{hp.put(owner.toString(),Math.min(100,value));setDirty();}}
    /** Discard only special bed ownership/HP from a completed run, not normal world blocks. */
    public void resetForNewRun(){hp.clear();states.clear();tokens.clear();causes.clear();setDirty();}
    public boolean permanentlyDestroyed(UUID owner){return state(owner)==2;}
}
