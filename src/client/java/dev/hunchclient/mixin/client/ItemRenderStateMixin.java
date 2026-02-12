package dev.hunchclient.mixin.client;

import dev.hunchclient.bridge.ModuleBridge;
import dev.hunchclient.bridge.module.IRevertAxeSwords;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Mixin to swap item models for the Revert Axe Swords feature.
 * In 1.21.10: ItemModelResolver.updateForLiving() is the entry point for living entity item rendering.
 */
@Mixin(ItemModelResolver.class)
public class ItemRenderStateMixin {

    /**
     * Modify the ItemStack before it's used to update the render state.
     * This allows us to visually replace swords with axes.
     * New signature: ItemModelResolver.updateForLiving(ItemStackRenderState, ItemStack, ItemDisplayContext, LivingEntity)
     */
    @ModifyVariable(
        method = "updateForLiving(Lnet/minecraft/client/renderer/item/ItemStackRenderState;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/world/entity/LivingEntity;)V",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private ItemStack hunchclient$swapAxeSwordModel(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return stack;
        }

        // Check if this item should be replaced with an axe
        IRevertAxeSwords ras = ModuleBridge.revertAxeSwords();
        if (ras != null) {
            ItemStack replacement = ras.getVisualReplacement(stack);
            if (replacement != null) {
                return replacement;
            }
        }

        return stack;
    }
}
