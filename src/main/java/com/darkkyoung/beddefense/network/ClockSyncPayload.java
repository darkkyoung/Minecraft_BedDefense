package com.darkkyoung.beddefense.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server-synced global Overworld clock and world elapsed ticks, in every dimension. */
public record ClockSyncPayload(long overworldDayTime, long challengeElapsedTicks, long challengeDay, int outcome) implements CustomPacketPayload {
    public static final Type<ClockSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("beddefense", "clock_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClockSyncPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG, ClockSyncPayload::overworldDayTime,
                    ByteBufCodecs.VAR_LONG, ClockSyncPayload::challengeElapsedTicks,
                    ByteBufCodecs.VAR_LONG, ClockSyncPayload::challengeDay,
                    ByteBufCodecs.VAR_INT, ClockSyncPayload::outcome,
                    ClockSyncPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
