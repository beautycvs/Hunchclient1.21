package dev.hunchclient.mixin.client;

import dev.hunchclient.module.ModuleManager;
import dev.hunchclient.util.ModuleCache;
import dev.hunchclient.module.impl.misc.ChatUtilsModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to handle MiddleClickGUI feature from ChatUtilsModule.
 * Converts left-clicks to middle-clicks in certain GUIs.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class HandledScreenSlotClickMixin extends Screen {

    @Shadow
    protected Slot hoveredSlot;

    protected HandledScreenSlotClickMixin() {
        super(null);
    }

    @Inject(method = "slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ClickType;)V", at = @At("HEAD"), cancellable = true)
    private void hunchclient$middleClickGui(Slot slot, int slotId, int button, ClickType actionType, CallbackInfo ci) {
        ChatUtilsModule chatUtils = ModuleCache.get(ChatUtilsModule.class);
        if (chatUtils == null) {
            return;
        }

        // Check if we should convert this click to middle-click
        if (chatUtils.shouldConvertToMiddleClick((Screen) (Object) this, slot, button)) {
            ci.cancel();

            // Re-click as middle button (button 2)
            Minecraft client = Minecraft.getInstance();
            if (client.gameMode != null && client.player != null) {
                client.gameMode.handleInventoryMouseClick(
                    ((AbstractContainerScreen<?>) (Object) this).getMenu().containerId,
                    slotId,
                    2, // Middle click
                    ClickType.CLONE,
                    client.player
                );
            }
        }
    }
}
