package com.darkkyoung.beddefense.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 0 = HUD toggle, 1 = debug preview cycle, sent only by /beddefense subcommands. */
public record HudControlPayload(int action) implements CustomPacketPayload {
    public static final Type<HudControlPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("beddefense","hud_control"));
    public static final StreamCodec<RegistryFriendlyByteBuf,HudControlPayload> CODEC =
        StreamCodec.composite(ByteBufCodecs.VAR_INT, HudControlPayload::action, HudControlPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
