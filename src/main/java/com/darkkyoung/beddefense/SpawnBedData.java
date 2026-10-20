package com.darkkyoung.beddefense;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Persistent per-player Spawn Bed positions, shared by registration and relocation.
 */
public final class SpawnBedData extends SavedData {
    private static final Codec<SpawnBedData> CODEC =
            Codec.unboundedMap(Codec.STRING, Codec.LONG)
                    .xmap(SpawnBedData::new, data -> Map.copyOf(data.beds));
    static final SavedDataType<SpawnBedData> TYPE =
            new SavedDataType<>(Identifier.fromNamespaceAndPath("beddefense", "spawn_beds"),
                    SpawnBedData::new, CODEC, null);

    private final Map<String, Long> beds;

    private SpawnBedData() {
        beds = new HashMap<>();
    }

    private SpawnBedData(Map<String, Long> saved) {
        beds = new HashMap<>(saved);
    }

    public static SpawnBedData get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(TYPE);
    }

    /** Old registered beds become ordinary world beds when beginning a new run. */
    public void resetForNewRun(){beds.clear();setDirty();}
    public boolean isRegistered(UUID player) {
        return beds.containsKey(player.toString());
    }

    public BlockPos getPosition(UUID player) {
        Long packed = beds.get(player.toString());
        return packed == null ? null : BlockPos.of(packed);
    }

    public Map<UUID,BlockPos> positions() {
        Map<UUID,BlockPos> result=new HashMap<>();
        beds.forEach((id,pos)->result.put(UUID.fromString(id),BlockPos.of(pos)));
        return result;
    }
    public void relocate(UUID owner,BlockPos foot) {
        if(!isRegistered(owner))throw new IllegalStateException("Unregistered bed");
        beds.put(owner.toString(),foot.asLong());setDirty();
    }
    public boolean registerFirst(UUID player, BlockPos bedFoot) {
        String key = player.toString();
        if (beds.containsKey(key)) {
            return false;
        }
        beds.put(key, bedFoot.asLong());
        setDirty();
        return true;
    }
}
