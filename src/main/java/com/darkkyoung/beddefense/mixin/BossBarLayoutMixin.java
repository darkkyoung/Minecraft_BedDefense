package com.darkkyoung.beddefense.mixin;
import com.darkkyoung.beddefense.client.BedDefenseHud;
import net.minecraft.client.gui.components.BossHealthOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Keep the dragon/vanilla boss bars below the fixed bed, raid count and warning area. */
@Mixin(BossHealthOverlay.class)
abstract class BossBarLayoutMixin {
    @ModifyConstant(method="extractRenderState",constant=@Constant(intValue=12))
    private int beddefense$reserveTop(int vanilla){return BedDefenseHud.vanillaBossBarTop(vanilla);}
}
