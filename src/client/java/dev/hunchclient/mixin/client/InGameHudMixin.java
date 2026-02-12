package dev.hunchclient.mixin.client;

import dev.hunchclient.bridge.ModuleBridge;
import dev.hunchclient.bridge.module.IRenderOptimize;
import dev.hunchclient.render.DarkModeRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class InGameHudMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void hunchclient$renderCustomHud(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        // NOTE: All HUD rendering now happens via HudEditorManager (unified system)
        // See HunchModClient where HudRenderCallback is registered
        // Each module registers its HudElements in onEnable()
    }

    // Disable vignette overlay
    @Inject(method = "renderVignette(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void hunchclient$disableVignette(GuiGraphics context, Entity entity, CallbackInfo ci) {
        IRenderOptimize ro = ModuleBridge.renderOpt();
        if (ro != null && ro.shouldDisableVignette()) {
            ci.cancel();
        }
    }

    // Disable vanilla armor HUD bar
    @Inject(method = "renderArmor", at = @At("HEAD"), cancellable = true)
    private static void hunchclient$disableVanillaArmor(GuiGraphics context, Player player, int i, int j, int k, int l, CallbackInfo ci) {
        IRenderOptimize ro = ModuleBridge.renderOpt();
        if (ro != null && ro.shouldDisableVanillaArmorHud()) {
            ci.cancel();
        }
    }

    // Disable potion effect icons on HUD (status effects)
    @Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true)
    private void hunchclient$disablePotionOverlay(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        IRenderOptimize ro = ModuleBridge.renderOpt();
        if (ro != null && ro.shouldHidePotionOverlay()) {
            ci.cancel();
        }
    }
}
