package com.darkkyoung.beddefense.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.DispenserScreen;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Only Bed Defense's recovery 3x3 inventory gets ghost recipe icons.
 * They are *render-only*: no ghost ItemStacks exist in the container, so nobody
 * can take or dupe them. A half-opacity shade approximates 50% translucency.
 * Real deposited items are rendered by vanilla on top of these placeholders.
 */
@Mixin(DispenserScreen.class)
abstract class RecoveryGhostRecipeMixin {
    @Inject(method="extractBackground",at=@At("TAIL"))
    private void beddefense$ghostSlots(GuiGraphicsExtractor graphics,int mouseX,int mouseY,
                                      float partialTicks,CallbackInfo info){
        DispenserScreen screen=(DispenserScreen)(Object)this;
        String title=screen.getTitle().getString();
        if(!title.contains("[BedDefense 복구]"))return;
        boolean second=title.contains("2차");
        AbstractContainerScreen<?> container=screen;
        int left=(graphics.guiWidth()-176)/2,top=(graphics.guiHeight()-166)/2;
        DispenserMenu menu=screen.getMenu();
        for(int i=0;i<9;i++){
            int x=left+62+(i%3)*18;
            int y=top+17+(i/3)*18;
            ItemStack actual=menu.getSlot(i).getItem();
            if(actual.isEmpty()){
                graphics.item(new ItemStack(i==4?net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(
                        net.minecraft.resources.Identifier.parse("minecraft:red_bed")):second?Items.EMERALD_BLOCK:Items.EMERALD),x,y);
                // ~50% faded preview, drawn *under* real item slots.
                graphics.fill(x,y,x+16,y+16,0x88000000);
            }else{
                boolean correct=i==4
                        ?Block.byItem(actual.getItem()) instanceof BedBlock
                        :actual.is(second?Items.EMERALD_BLOCK:Items.EMERALD);
                if(!correct)graphics.fill(x,y,x+16,y+16,0x55FF3333);
            }
        }
    }
}
