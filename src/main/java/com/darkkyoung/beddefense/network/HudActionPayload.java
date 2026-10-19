package com.darkkyoung.beddefense.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Authorized server to client HUD commands. 0 toggle display, 1 preview cycle. */
public record HudActionPayload(int action) implements CustomPacketPayload {
    public static final Type<HudActionPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("beddefense","hud_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf,HudActionPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT,HudActionPayload::action,HudActionPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
