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

/**
 * A separate saved record keeps the old alpha.4 positional registry readable.
 *
 * States: 0 not registered; 1 installed; 2 raid-destroyed permanently;
 *         3 manually removed (yellow). HP persists through state 3.
 */
public final class BedVitalData extends SavedData {
    public static final int MAX_HEALTH = 100;
    private static final Codec<BedVitalData> CODEC = RecordCodecBuilder.create(i->i.group(
        Codec.unboundedMap(Codec.STRING,Codec.INT).fieldOf("hp").forGetter(x->Map.copyOf(x.hp)),
        Codec.unboundedMap(Codec.STRING,Codec.INT).fieldOf("states").forGetter(x->Map.copyOf(x.states))
    ).apply(i,BedVitalData::new));
    private static final SavedDataType<BedVitalData> TYPE =
            new SavedDataType<>(Identifier.fromNamespaceAndPath("beddefense","bed_vitals"),
                BedVitalData::new,CODEC,null);
    private final Map<String,Integer> hp;
    private final Map<String,Integer> states;

    private BedVitalData() { this(Map.of(),Map.of()); }
    private BedVitalData(Map<String,Integer> hp,Map<String,Integer> states) {
        this.hp=new HashMap<>(hp);
        this.states=new HashMap<>(states);
    }
    public static BedVitalData get(ServerLevel world) { return world.getDataStorage().computeIfAbsent(TYPE); }
    public int state(UUID owner) { return states.getOrDefault(owner.toString(),0); }
    public int hp(UUID owner) { return hp.getOrDefault(owner.toString(),0); }
    public void register(UUID owner) {
        String id=owner.toString();
        if (states.containsKey(id)) return;
        states.put(id,1);hp.put(id,MAX_HEALTH);setDirty();
    }
    public boolean unplace(UUID owner) {
        String id=owner.toString();
        if (state(owner)!=1) return false;
        states.put(id,3);setDirty();return true;
    }
    public int damage(UUID owner, int amount) {
        String id=owner.toString();
        if (state(owner)!=1||amount<=0) return hp(owner);
        int next=Math.max(0,hp(owner)-amount);
        hp.put(id,next);
        if(next==0) states.put(id,2);
        setDirty();
        return next;
    }
    public boolean destroy(UUID owner) {
        String id=owner.toString();
        if(state(owner)!=1)return false;
        states.put(id,2);hp.put(id,0);setDirty();return true;
    }

    public boolean permanentlyDestroyed(UUID owner) {return state(owner)==2;}
}
