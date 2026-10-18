package com.darkkyoung.beddefense.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server-authoritative overworld time, shared between every dimension.
 * Does not imply that raids have been implemented.
 */
public record ClockSyncPayload(long overworldDayTime) implements CustomPacketPayload {
    public static final Type<ClockSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("beddefense", "clock_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClockSyncPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_LONG, ClockSyncPayload::overworldDayTime, ClockSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
