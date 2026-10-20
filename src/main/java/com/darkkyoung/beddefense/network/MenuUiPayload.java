package com.darkkyoung.beddefense.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server-authorized AllBlocks-style chat transitions.
 * false = clear earlier chat before rendering the new menu;
 * true  = clear chat and close the chat/setup screen before countdown.
 */
public record MenuUiPayload(boolean closeScreen) implements CustomPacketPayload {
    public static final Type<MenuUiPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("beddefense", "menu_ui"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MenuUiPayload> CODEC =
            StreamCodec.ofMember(MenuUiPayload::write, MenuUiPayload::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeBoolean(closeScreen);
    }

    private static MenuUiPayload read(RegistryFriendlyByteBuf buf) {
        return new MenuUiPayload(buf.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
