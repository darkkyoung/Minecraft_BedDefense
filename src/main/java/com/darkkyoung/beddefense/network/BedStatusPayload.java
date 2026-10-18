package com.darkkyoung.beddefense.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 0 = unregistered/unplaced, 1 = placed, 2 = destroyed (future). */
public record BedStatusPayload(int state) implements CustomPacketPayload {
    public static final Type<BedStatusPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("beddefense", "bed_status"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BedStatusPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, BedStatusPayload::state,
                    BedStatusPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
