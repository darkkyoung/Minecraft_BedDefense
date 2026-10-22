package com.darkkyoung.beddefense.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server-authorized AllBlocks-style chat transitions.
 * closeScreen: remove earlier chat and close the current chat/setup screen for the countdown.
 * welcome: expand only the initial [게임 시작] introduction in the client chat HUD.
 * All other menu transitions clear the temporary welcome layout.
 */
public record MenuUiPayload(boolean closeScreen, boolean welcome) implements CustomPacketPayload {
    public static final Type<MenuUiPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("beddefense", "menu_ui"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MenuUiPayload> CODEC =
            StreamCodec.ofMember(MenuUiPayload::write, MenuUiPayload::read);

    public MenuUiPayload(boolean closeScreen) {
        this(closeScreen, false);
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeBoolean(closeScreen);
        buf.writeBoolean(welcome);
    }

    private static MenuUiPayload read(RegistryFriendlyByteBuf buf) {
        return new MenuUiPayload(buf.readBoolean(), buf.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
