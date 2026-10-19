package com.darkkyoung.beddefense;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Single-world challenge configuration; additive fields preserve alpha.5 saves. */
public final class ChallengeData extends SavedData {
    private static final Codec<ChallengeData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("start_game_tick").forGetter(data -> data.startGameTick),
            Codec.LONG.fieldOf("start_day_index").forGetter(data -> data.startDayIndex),
            Codec.STRING.optionalFieldOf("difficulty", "normal").forGetter(data -> data.difficulty),
            Codec.INT.optionalFieldOf("raid_stride", 1).forGetter(data -> data.raidStride),
            Codec.BOOL.optionalFieldOf("reset_world_time", false).forGetter(data -> data.resetWorldTime)
    ).apply(instance, ChallengeData::new));
    private static final SavedDataType<ChallengeData> TYPE =
            new SavedDataType<>(Identifier.fromNamespaceAndPath("beddefense", "challenge"),
                    ChallengeData::new, CODEC, null);

    private long startGameTick;
    private long startDayIndex;
    private String difficulty;
    private int raidStride;
    private boolean resetWorldTime;

    private ChallengeData() { this(-1L, -1L, "normal", 1, false); }
    private ChallengeData(long startGameTick, long startDayIndex,
                          String difficulty, int raidStride, boolean resetWorldTime) {
        this.startGameTick = startGameTick;
        this.startDayIndex = startDayIndex;
        this.difficulty = difficulty;
        this.raidStride = Math.max(1, Math.min(10, raidStride));
        this.resetWorldTime = resetWorldTime;
    }
    public static ChallengeData get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(TYPE);
    }
    public boolean isStarted() { return startGameTick >= 0; }
    public ChallengeDifficulty difficulty() { return ChallengeDifficulty.parse(difficulty); }
    public int raidStride() { return raidStride; }
    public boolean resetWorldTime() { return resetWorldTime; }
    public boolean start(long gameTick, long worldClock, ChallengeDifficulty level, int stride, boolean reset) {
        if (isStarted()) return false;
        startGameTick = gameTick;
        startDayIndex = Math.floorDiv(worldClock, 24_000L);
        difficulty = level.key();
        raidStride = Math.max(1, Math.min(10, stride));
        resetWorldTime = reset;
        setDirty();
        return true;
    }
    public long elapsedTicks(long gameTick) {
        return isStarted() ? Math.max(0L, gameTick - startGameTick) : -1L;
    }
    public long day(long worldClock) {
        return isStarted() ? Math.max(1L, Math.floorDiv(worldClock, 24_000L) - startDayIndex + 1L) : -1L;
    }
    public int raidIndex(long challengeDay) {
        return (int) Math.min(100, 1 + Math.max(0, challengeDay - 1) * raidStride);
    }
}
