package dev.hunchclient.mixin.client;

import dev.hunchclient.bridge.ModuleBridge;
import dev.hunchclient.bridge.module.INameProtect;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * PlayerListHud Mixin for NameProtect
 * Hooks tab list name rendering to sanitize player names
 * ONLY modifies during rendering - does NOT change actual PlayerListEntry data
 */
@Mixin(PlayerTabOverlay.class)
public class PlayerListHudMixin {

    @Inject(
        method = "getNameForDisplay(Lnet/minecraft/client/multiplayer/PlayerInfo;)Lnet/minecraft/network/chat/Component;",
        at = @At("RETURN"),
        cancellable = true
    )
    private void hunchclient$sanitizeTabListName(PlayerInfo entry, CallbackInfoReturnable<Component> cir) {
        INameProtect module = ModuleBridge.nameProtect();
        if (module != null) {
            Component original = cir.getReturnValue();
            if (original != null) {
                Component sanitized = module.sanitizeText(original);
                if (sanitized != original) {
                    cir.setReturnValue(sanitized);
                }
            }
        }
    }
}
