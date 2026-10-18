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
 * Persistent per-player first Spawn Bed positions for the visual prototype.
 * This is NOT a full bed item/HP/respawn/challenge implementation.
 */
public final class SpawnBedData extends SavedData {
    private static final Codec<SpawnBedData> CODEC =
            Codec.unboundedMap(Codec.STRING, Codec.LONG)
                    .xmap(SpawnBedData::new, data -> Map.copyOf(data.beds));
    private static final SavedDataType<SpawnBedData> TYPE =
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

    public boolean isRegistered(UUID player) {
        return beds.containsKey(player.toString());
    }

    public BlockPos getPosition(UUID player) {
        Long packed = beds.get(player.toString());
        return packed == null ? null : BlockPos.of(packed);
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
