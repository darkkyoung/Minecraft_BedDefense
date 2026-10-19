package com.darkkyoung.beddefense.client;

import com.darkkyoung.beddefense.network.ClockSyncPayload;
import com.darkkyoung.beddefense.network.BedStatusPayload;
import com.darkkyoung.beddefense.network.HudActionPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class BedDefenseClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(ClockSyncPayload.TYPE,
                (payload, context) -> context.client().execute(
                        () -> BedDefenseHud.acceptServerTime(
                                payload.overworldDayTime(), payload.challengeElapsedTicks(), payload.challengeDay())));

        ClientPlayNetworking.registerGlobalReceiver(BedStatusPayload.TYPE,
                (payload, context) -> context.client().execute(
                        () -> BedDefenseHud.acceptBedState(payload.state())));

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("beddefense", "hud"),
                BedDefenseHud::render);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level == null || client.player == null) {
                BedDefenseHud.onDisconnected();
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(HudActionPayload.TYPE,
                (payload,context) -> context.client().execute(() -> {
                    if(payload.action()==0) BedDefenseHud.toggle();
                    if(payload.action()==1) BedDefenseHud.nextPreview();
                }));

    }
}
