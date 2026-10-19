package com.darkkyoung.beddefense;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Persistent setup values, separate from the already-shipped challenge save codec. */
public final class ChallengeOptionsData extends SavedData {
    private static final Codec<ChallengeOptionsData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("difficulty").forGetter(x -> x.difficulty),
            Codec.BOOL.fieldOf("reset_world_clock").forGetter(x -> x.resetWorldClock),
            Codec.INT.fieldOf("raid_stride").forGetter(x -> x.raidStride)
    ).apply(i, ChallengeOptionsData::new));
    private static final SavedDataType<ChallengeOptionsData> TYPE =
            new SavedDataType<>(Identifier.fromNamespaceAndPath("beddefense","challenge_options"),
                    ChallengeOptionsData::new, CODEC, null);

    private String difficulty;
    private boolean resetWorldClock;
    private int raidStride;

    private ChallengeOptionsData() {
        this("보통", false, 1);
    }
    private ChallengeOptionsData(String difficulty, boolean resetWorldClock, int raidStride) {
        this.difficulty = difficulty;
        this.resetWorldClock = resetWorldClock;
        this.raidStride = Math.max(1, Math.min(100, raidStride));
    }
    public static ChallengeOptionsData get(ServerLevel world) {
        return world.getDataStorage().computeIfAbsent(TYPE);
    }
    public void update(String difficulty, boolean resetWorldClock, int raidStride) {
        this.difficulty = difficulty;
        this.resetWorldClock = resetWorldClock;
        this.raidStride = Math.max(1, Math.min(100, raidStride));
        setDirty();
    }
    public String difficulty() { return difficulty; }
    public boolean resetWorldClock() { return resetWorldClock; }
    public int raidStride() { return raidStride; }
}
