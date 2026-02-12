package dev.hunchclient.mixin.client;

import dev.hunchclient.bridge.ModuleBridge;
import dev.hunchclient.bridge.module.IF7Sim;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to enable insta-mining with pickaxe when F7Sim is enabled
 */
@Mixin(Player.class)
public abstract class PlayerEntityBlockBreakMixin {

    @Inject(method = "getDestroySpeed", at = @At("RETURN"), cancellable = true)
    private void hunchclient$instaMineInF7Sim(BlockState block, CallbackInfoReturnable<Float> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || !mc.isLocalServer() || mc.player == null) {
            return;
        }

        IF7Sim f7Sim = ModuleBridge.f7sim();
        if (f7Sim == null || !f7Sim.isEnabled()) {
            return;
        }

        // Check if holding a pickaxe
        ItemStack mainHand = mc.player.getMainHandItem();
        if (isPickaxe(mainHand)) {
            // Insta-mine with pickaxe (works in both creative and survival)
            cir.setReturnValue(1000.0f);
        } else {
            // Disable mining without pickaxe
            cir.setReturnValue(0.0f);
        }
    }

    private boolean isPickaxe(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return stack.is(Items.WOODEN_PICKAXE) ||
               stack.is(Items.STONE_PICKAXE) ||
               stack.is(Items.IRON_PICKAXE) ||
               stack.is(Items.GOLDEN_PICKAXE) ||
               stack.is(Items.DIAMOND_PICKAXE) ||
               stack.is(Items.NETHERITE_PICKAXE);
    }
}
