package com.darkkyoung.beddefense;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * One challenge per world (single-player MVP).
 * Starting the challenge is an explicit, persistent one-way state transition.
 * This does not implement raids, permadeath or the Ender Dragon victory condition.
 */
public final class ChallengeData extends SavedData {
    private static final Codec<ChallengeData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("start_game_tick").forGetter(data -> data.startGameTick),
            Codec.LONG.fieldOf("start_day_index").forGetter(data -> data.startDayIndex)
    ).apply(instance, ChallengeData::new));

    private static final SavedDataType<ChallengeData> TYPE =
            new SavedDataType<>(Identifier.fromNamespaceAndPath("beddefense", "challenge"),
                    ChallengeData::new, CODEC, null);

    private long startGameTick;
    private long startDayIndex;

    private ChallengeData() {
        this(-1L, -1L);
    }

    private ChallengeData(long startGameTick, long startDayIndex) {
        this.startGameTick = startGameTick;
        this.startDayIndex = startDayIndex;
    }

    public static ChallengeData get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean isStarted() {
        return startGameTick >= 0L;
    }

    public boolean start(long gameTick, long worldClock) {
        if (isStarted()) return false;
        startGameTick = gameTick;
        startDayIndex = Math.floorDiv(worldClock, 24_000L);
        setDirty();
        return true;
    }

    public long elapsedTicks(long gameTick) {
        return isStarted() ? Math.max(0L, gameTick - startGameTick) : -1L;
    }

    public long day(long worldClock) {
        return isStarted() ? Math.max(1L, Math.floorDiv(worldClock, 24_000L) - startDayIndex + 1L) : -1L;
    }
}
