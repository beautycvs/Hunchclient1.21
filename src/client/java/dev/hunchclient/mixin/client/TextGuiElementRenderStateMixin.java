package dev.hunchclient.mixin.client;

import dev.hunchclient.bridge.ModuleBridge;
import dev.hunchclient.bridge.module.INameProtect;
import net.minecraft.client.gui.render.state.GuiTextRenderState;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

/**
 * DEEPEST HOOK POSSIBLE for NameProtect
 * Intercepts ALL text at the render state level before it's rendered
 * This catches EVERYTHING: Scoreboard, Tooltips, Chat, Nametags, GUI text, etc.
 */
@Mixin(GuiTextRenderState.class)
public class TextGuiElementRenderStateMixin {

    @ModifyVariable(
        method = "<init>",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private static FormattedCharSequence hunchclient$sanitizeOrderedTextBeforeConstruction(FormattedCharSequence orderedText) {
        INameProtect module = ModuleBridge.nameProtect();
        if (module != null && orderedText != null) {
            return module.sanitizeOrderedText(orderedText);
        }
        return orderedText;
    }
}
