package dev.hunchclient.mixin.client;

import dev.hunchclient.bridge.ModuleBridge;
import dev.hunchclient.bridge.module.INameProtect;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * TextRenderer Mixin for NameProtect - COMPLETE COVERAGE
 * Hooks ALL text rendering methods with correct 1.21 signatures
 * ONLY modifies during rendering - does NOT change actual data
 */
@Mixin(value = Font.class, priority = 1000)
public class TextRendererMixin {

    private INameProtect hunchclient$getModule() {
        return ModuleBridge.nameProtect();
    }

    // ===== HOOK ALL DRAW METHODS (1.21 signatures) =====

    // Hook draw(String, ...) - 10 parameter version
    @ModifyVariable(
        method = "drawInBatch(Ljava/lang/String;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)V",
        at = @At("HEAD"),
        argsOnly = true
    )
    private String hunchclient$sanitizeDrawString(String text) {
        INameProtect module = hunchclient$getModule();
        // Always apply NameProtect (remote replacements always active, self-replacement only when enabled)
        if (module != null && text != null) {
            return module.sanitizeString(text);
        }
        return text;
    }

    // Hook draw(Text, ...)
    @ModifyVariable(
        method = "drawInBatch(Lnet/minecraft/network/chat/Component;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)V",
        at = @At("HEAD"),
        argsOnly = true
    )
    private Component hunchclient$sanitizeDrawText(Component text) {
        INameProtect module = hunchclient$getModule();
        // Always apply NameProtect (remote replacements always active, self-replacement only when enabled)
        if (module != null && text != null) {
            return module.sanitizeText(text);
        }
        return text;
    }

    // Hook draw(OrderedText, ...)
    @ModifyVariable(
        method = "drawInBatch(Lnet/minecraft/util/FormattedCharSequence;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)V",
        at = @At("HEAD"),
        argsOnly = true
    )
    private FormattedCharSequence hunchclient$sanitizeDrawOrderedText(FormattedCharSequence text) {
        INameProtect module = hunchclient$getModule();
        // Always apply NameProtect (remote replacements always active, self-replacement only when enabled)
        if (module != null && text != null) {
            return module.sanitizeOrderedText(text);
        }
        return text;
    }

    // ===== HOOK WRAPPING METHODS =====

    @ModifyVariable(
        method = "split(Lnet/minecraft/network/chat/FormattedText;I)Ljava/util/List;",
        at = @At("HEAD"),
        argsOnly = true
    )
    private FormattedText hunchclient$sanitizeWrapLines(FormattedText text) {
        INameProtect module = hunchclient$getModule();
        // Always apply NameProtect (remote replacements always active, self-replacement only when enabled)
        if (module != null && text != null) {
            return module.sanitizeStringVisitable(text);
        }
        return text;
    }

    @ModifyVariable(
        method = "splitIgnoringLanguage(Lnet/minecraft/network/chat/FormattedText;I)Ljava/util/List;",
        at = @At("HEAD"),
        argsOnly = true
    )
    private FormattedText hunchclient$sanitizeWrapLinesWithoutLanguage(FormattedText text) {
        INameProtect module = hunchclient$getModule();
        // Always apply NameProtect (remote replacements always active, self-replacement only when enabled)
        if (module != null && text != null) {
            return module.sanitizeStringVisitable(text);
        }
        return text;
    }

    // ===== HOOK WIDTH CALCULATION =====

    @ModifyVariable(method = "width(Ljava/lang/String;)I", at = @At("HEAD"), argsOnly = true)
    private String hunchclient$sanitizeGetWidthString(String text) {
        INameProtect module = hunchclient$getModule();
        // Always apply NameProtect (remote replacements always active, self-replacement only when enabled)
        if (module != null && text != null) {
            return module.sanitizeString(text);
        }
        return text;
    }

    @ModifyVariable(
        method = "width(Lnet/minecraft/network/chat/FormattedText;)I",
        at = @At("HEAD"),
        argsOnly = true
    )
    private FormattedText hunchclient$sanitizeGetWidthStringVisitable(FormattedText text) {
        INameProtect module = hunchclient$getModule();
        // Always apply NameProtect (remote replacements always active, self-replacement only when enabled)
        if (module != null && text != null) {
            return module.sanitizeStringVisitable(text);
        }
        return text;
    }

    @ModifyVariable(method = "width(Lnet/minecraft/util/FormattedCharSequence;)I", at = @At("HEAD"), argsOnly = true)
    private FormattedCharSequence hunchclient$sanitizeGetWidthOrderedText(FormattedCharSequence text) {
        INameProtect module = hunchclient$getModule();
        // Always apply NameProtect (remote replacements always active, self-replacement only when enabled)
        if (module != null && text != null) {
            return module.sanitizeOrderedText(text);
        }
        return text;
    }

    // ===== HOOK PREPARE METHOD (LOWEST LEVEL) =====
    // NOTE: prepareText and plainSubstrByWidth methods do not exist in Mojang mappings Font class
    // The drawInBatch hooks above provide sufficient coverage for NameProtect
}
