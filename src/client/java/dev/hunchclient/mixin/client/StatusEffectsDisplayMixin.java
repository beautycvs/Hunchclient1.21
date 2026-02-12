package dev.hunchclient.mixin.client;

import dev.hunchclient.bridge.ModuleBridge;
import dev.hunchclient.bridge.module.IRenderOptimize;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.EffectsInInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(EffectsInInventory.class)
public class StatusEffectsDisplayMixin {

    @Inject(method = "renderEffects(Lnet/minecraft/client/gui/GuiGraphics;II)V", at = @At("HEAD"), cancellable = true)
    private void hunchclient$hideInventoryEffects(GuiGraphics context, int mouseX, int mouseY, CallbackInfo ci) {
        IRenderOptimize ro = ModuleBridge.renderOpt();
        if (ro != null && ro.shouldHideInventoryEffects()) {
            ci.cancel();
        }
    }
}
