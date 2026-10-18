package com.darkkyoung.beddefense.client;

import com.darkkyoung.beddefense.network.ClockSyncPayload;
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
        // Receiving over the network rather than using the local dimension's fixed sky time.
        ClientPlayNetworking.registerGlobalReceiver(ClockSyncPayload.TYPE,
                (payload, context) -> context.client().execute(
                        () -> BedDefenseHud.acceptServerTime(payload.overworldDayTime())));

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("beddefense", "hud"),
                BedDefenseHud::render);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level == null || client.player == null) {
                BedDefenseHud.onDisconnected();
            }
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommands.literal("bdhud")
                        .executes(ctx -> {
                            BedDefenseHud.toggle();
                            ctx.getSource().sendFeedback(
                                    Component.literal("[Bed Defense] HUD " +
                                            (BedDefenseHud.isVisible() ? "ON" : "OFF")));
                            return 1;
                        })
                        .then(ClientCommands.literal("preview")
                                .executes(ctx -> {
                                    BedDefenseHud.togglePreview();
                                    ctx.getSource().sendFeedback(
                                            Component.literal("[Bed Defense] UI preview " +
                                                    (BedDefenseHud.isPreview() ? "ON" : "OFF") +
                                                    " (visual only; no raid or Bed HP simulation)."));
                                    return 1;
                                })))
        );
    }
}
