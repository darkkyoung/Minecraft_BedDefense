package com.darkkyoung.beddefense.mixin;

import com.darkkyoung.beddefense.client.BedDefenseHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(targets="net.minecraft.client.gui.Hud$HeartType")
abstract class HardcoreHeartsMixin {
    @ModifyVariable(method="getSprite",at=@At("HEAD"),argsOnly=true,ordinal=0)
    private boolean beddefense$hardcore(boolean vanilla){return vanilla||BedDefenseHud.hardcoreRisk();}
}
