package dev.hunchclient.mixin.client;

import dev.hunchclient.module.ModuleManager;
import dev.hunchclient.module.impl.dungeons.AutoSuperboomModule;
import dev.hunchclient.module.impl.dungeons.LeftClickEtherwarpModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseMixin {

    /**
     * Detektiert Left-Clicks für LeftClickEtherwarp und AutoSuperboom
     * PRIORITÄT: AutoSuperboom > LeftClickEtherwarp
     * Updated for 1.21.10: onMouseButton now uses MouseInput object
     */
    @Inject(method = "onButton(JLnet/minecraft/client/input/MouseButtonInfo;I)V", at = @At("HEAD"), cancellable = true)
    private void onMouseButton(long window, net.minecraft.client.input.MouseButtonInfo input, int action, CallbackInfo ci) {
        // Extract button from MouseInput object
        int button = input.button();

        Minecraft mc = Minecraft.getInstance();

        // Button 0 = Left Click, Action 1 = Press
        if (button == 0 && action == 1) {
            // Nur wenn kein GUI offen ist
            if (mc.screen == null && mc.player != null) {
                ModuleManager manager = ModuleManager.getInstance();

                // AutoSuperboom Module (PRIORITÄT 1 - wird ZUERST geprüft!)
                AutoSuperboomModule superboomModule = manager.getModule(AutoSuperboomModule.class);
                if (superboomModule != null && superboomModule.isEnabled()) {
                    boolean triggered = superboomModule.onLeftClick();

                    // Wenn AutoSuperboom getriggert wurde, KEINE anderen Module ausführen!
                    if (triggered) {
                        return;
                    }
                }

                // LeftClickEtherwarp Module (PRIORITÄT 2 - nur wenn AutoSuperboom NICHT triggered!)
                LeftClickEtherwarpModule etherwarpModule = manager.getModule(LeftClickEtherwarpModule.class);
                if (etherwarpModule != null && etherwarpModule.isEnabled()) {
                    etherwarpModule.onLeftClick();
                }
            }
        }
    }
}
