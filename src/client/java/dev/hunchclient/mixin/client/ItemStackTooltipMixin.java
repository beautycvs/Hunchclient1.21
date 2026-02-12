package dev.hunchclient.mixin.client;

import dev.hunchclient.bridge.ModuleBridge;
import dev.hunchclient.bridge.module.INameProtect;
import dev.hunchclient.util.ModuleCache;
import dev.hunchclient.module.impl.sbd.PartyFinderModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Mixin to modify ItemStack tooltips for Party Finder and hide Croesus spoilers
 * Also applies NameProtect to all tooltip lines (DrawContext doesn't handle tooltips globally)
 */
@Mixin(ItemStack.class)
public class ItemStackTooltipMixin {

    @Inject(
        method = "getTooltipLines",
        at = @At("HEAD"),
        cancellable = true
    )
    private void hunchclient$hideCroesusTooltips(
        Item.TooltipContext context,
        Player player,
        TooltipFlag type,
        CallbackInfoReturnable<List<Component>> cir
    ) {
        Minecraft mc = Minecraft.getInstance();
        Screen currentScreen = mc.screen;

        // Only hide tooltips in chest GUIs
        if (!(currentScreen instanceof ContainerScreen containerScreen)) {
            return;
        }

        String title = containerScreen.getTitle().getString();

        // Check if we're in Croesus or a dungeon chest - hide tooltips to prevent spoilers
        if (shouldHideTooltip(title)) {
            cir.setReturnValue(List.of(Component.literal("§7...")));
        }
    }

    @Inject(
        method = "getTooltipLines",
        at = @At("RETURN")
    )
    private void hunchclient$modifyTooltip(
        Item.TooltipContext context,
        Player player,
        TooltipFlag type,
        CallbackInfoReturnable<List<Component>> cir
    ) {
        List<Component> tooltip = cir.getReturnValue();
        if (tooltip.isEmpty()) {
            return;
        }

        // PartyFinder modifications (adds PB times etc.)
        PartyFinderModule partyFinder = ModuleCache.get(PartyFinderModule.class);
        if (partyFinder != null && partyFinder.isEnabled()) {
            ItemStack stack = (ItemStack) (Object) this;
            partyFinder.modifyTooltip(stack, tooltip);
        }

        // Apply NameProtect to ALL tooltip lines
        // This must be done here because DrawContext doesn't handle tooltips globally
        // (doing it there would break other mods)
        INameProtect nameProtect = ModuleBridge.nameProtect();
        if (nameProtect != null) {
            for (int i = 0; i < tooltip.size(); i++) {
                Component original = tooltip.get(i);
                Component sanitized = nameProtect.sanitizeText(original);
                if (sanitized != original) {
                    tooltip.set(i, sanitized);
                }
            }
        }
    }

    private boolean shouldHideTooltip(String title) {
        // Hide tooltips in Croesus and dungeon chest GUIs to prevent spoilers
        String lower = title.toLowerCase();
        return lower.contains("croesus") ||
               lower.contains("bedrock chest") ||
               lower.contains("obsidian chest") ||
               lower.contains("diamond chest") ||
               lower.contains("emerald chest") ||
               lower.contains("gold chest") ||
               (lower.contains("catacombs") && lower.contains("chest"));
    }
}
