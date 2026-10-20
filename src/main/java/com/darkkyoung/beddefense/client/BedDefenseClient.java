package com.darkkyoung.beddefense.client;

import com.darkkyoung.beddefense.network.ClockSyncPayload;
import com.darkkyoung.beddefense.network.BedStatusPayload;
import com.darkkyoung.beddefense.network.HudControlPayload;
import com.darkkyoung.beddefense.network.RaidStatusPayload;
import com.darkkyoung.beddefense.network.MenuUiPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.resources.Identifier;

public final class BedDefenseClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(ClockSyncPayload.TYPE,
                (payload, context) -> context.client().execute(
                        () -> BedDefenseHud.acceptServerTime(
                                payload.overworldDayTime(), payload.challengeElapsedTicks(), payload.challengeDay(), payload.outcome())));
        ClientPlayNetworking.registerGlobalReceiver(BedStatusPayload.TYPE,
                (payload, context) -> context.client().execute(
                        () -> BedDefenseHud.acceptBedState(payload.state(),payload.hp(),payload.maxHp())));
        ClientPlayNetworking.registerGlobalReceiver(HudControlPayload.TYPE,
                (payload, context) -> context.client().execute(() -> {
                    if (payload.action() == 0) BedDefenseHud.toggle();
                    else if (payload.action() == 1) BedDefenseHud.nextPreview();
                }));

        ClientPlayNetworking.registerGlobalReceiver(RaidStatusPayload.TYPE,
                (payload, context) -> context.client().execute(
                    () -> BedDefenseHud.acceptRaidStatus(payload.day(),payload.remaining(),payload.total())));
        // Same clear/close protocol as AllBlocks master. Serialized packets arrive
        // before subsequent menu/title messages, preventing duplicated menu blocks.
        ClientPlayNetworking.registerGlobalReceiver(MenuUiPayload.TYPE,
                (payload, context) -> context.client().execute(() -> {
                    context.client().gui.hud.getChat().clearMessages(false);
                    if (payload.closeScreen()) context.client().gui.setScreen(null);
                }));
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("beddefense", "hud"),
                BedDefenseHud::render);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level == null || client.player == null) BedDefenseHud.onDisconnected();
        });
    }
}
