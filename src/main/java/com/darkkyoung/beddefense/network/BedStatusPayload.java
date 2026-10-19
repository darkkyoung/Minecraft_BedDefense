package com.darkkyoung.beddefense.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 0=not registered, 1=placed, 2=permanently destroyed, 3=player unplaced. */
public record BedStatusPayload(int state, int hp, int maxHp) implements CustomPacketPayload {
    public static final Type<BedStatusPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("beddefense", "bed_status"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BedStatusPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, BedStatusPayload::state,
                    ByteBufCodecs.VAR_INT, BedStatusPayload::hp,
                    ByteBufCodecs.VAR_INT, BedStatusPayload::maxHp,
                    BedStatusPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
