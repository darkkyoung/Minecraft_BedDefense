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
        ClientPlayNetworking.registerGlobalReceiver(ClockSyncPayload.TYPE,
                (payload, context) -> context.client().execute(
                        () -> BedDefenseHud.acceptServerTime(
                                payload.overworldDayTime(), payload.elapsedWorldTicks())));

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
                                    Component.literal("[베드 디펜스] HUD " +
                                            (BedDefenseHud.isVisible() ? "표시" : "숨김")));
                            return 1;
                        })
                        .then(ClientCommands.literal("preview")
                                .executes(ctx -> {
                                    String status = BedDefenseHud.nextPreview();
                                    ctx.getSource().sendFeedback(Component.literal(
                                            "[베드 디펜스] UI 시험: " + status + " (실제 침대나 레이드는 변경되지 않음)"));
                                    return 1;
                                })))
        );
    }
}
