package com.darkkyoung.beddefense.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Current server-side raid state, not a UI simulation. */
public record RaidStatusPayload(int day, int remaining, int total) implements CustomPacketPayload {
    public static final Type<RaidStatusPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("beddefense","raid_status"));
    public static final StreamCodec<RegistryFriendlyByteBuf,RaidStatusPayload> CODEC =
            StreamCodec.composite(
                ByteBufCodecs.VAR_INT, RaidStatusPayload::day,
                ByteBufCodecs.VAR_INT, RaidStatusPayload::remaining,
                ByteBufCodecs.VAR_INT, RaidStatusPayload::total,
                RaidStatusPayload::new
            );
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
