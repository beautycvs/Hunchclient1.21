package dev.hunchclient.mixin.client;

import dev.hunchclient.bridge.ModuleBridge;
import dev.hunchclient.bridge.module.INameProtect;
import net.minecraft.client.GuiMessage;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiMessage.class)
public class ChatHudLineMixin {

    @Shadow
    @Final
    @Mutable
    private Component content;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void hunchclient$sanitizeLineContent(CallbackInfo ci) {
        INameProtect module = ModuleBridge.nameProtect();
        // Always apply NameProtect (remote replacements always active, self-replacement only when enabled)
        if (module == null) {
            return;
        }

        if (content != null) {
            Component sanitized = module.sanitizeText(content);
            if (sanitized != content) {
                content = sanitized;
            }
        }
    }
}
