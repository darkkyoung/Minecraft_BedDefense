package com.darkkyoung.beddefense.mixin;

import com.darkkyoung.beddefense.client.WelcomeChatDisplay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla chat's unfocused height clips the introduction at its default number
 * of visible lines. Expand it for the initial Bed Defense welcome only.
 * Other chats and all permanent chat settings retain the vanilla behavior.
 */
@Mixin(ChatComponent.class)
abstract class WelcomeChatHeightMixin {
    @Inject(method="getHeight()I",at=@At("RETURN"),cancellable=true)
    private void beddefense$expandFirstWelcome(CallbackInfoReturnable<Integer> callback) {
        if (!WelcomeChatDisplay.showing()) return;
        Minecraft minecraft=Minecraft.getInstance();
        callback.setReturnValue(WelcomeChatDisplay.height(callback.getReturnValue(),
                minecraft.getWindow().getGuiScaledHeight(),
                minecraft.options.chatScale().get()));
    }
}
