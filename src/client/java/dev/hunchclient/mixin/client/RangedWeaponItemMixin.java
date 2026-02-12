package dev.hunchclient.mixin.client;

import dev.hunchclient.bridge.ModuleBridge;
import dev.hunchclient.bridge.module.IF7Sim;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;

@Mixin(ProjectileWeaponItem.class)
public class RangedWeaponItemMixin {

    /**
     * Terminal Mode: Modify projectiles list to shoot 3 arrows.
     * This intercepts the projectiles list before shoot() processes it.
     * Note: In 1.21.10 shootAll() was renamed to shoot()
     */
    @ModifyVariable(
        method = "shoot",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private List<ItemStack> hunchclient$terminalMode(
            List<ItemStack> projectiles,
            ServerLevel world,
            LivingEntity shooter,
            InteractionHand hand,
            ItemStack stack,
            List<ItemStack> projectilesParam,
            float speed,
            float divergence,
            boolean critical,
            @Nullable LivingEntity target) {

        // Only apply to bow items
        if (!(stack.getItem() instanceof BowItem)) {
            return projectiles;
        }

        if (shooter instanceof Player) {
            IF7Sim f7sim = ModuleBridge.f7sim();
            if (f7sim != null && f7sim.isEnabled() && f7sim.isTerminatorMode()) {
                // Terminal mode: shoot 3 arrows
                if (!projectiles.isEmpty()) {
                    List<ItemStack> tripleShot = new ArrayList<>();
                    ItemStack arrow = projectiles.get(0);
                    // Create 3 copies for triple shot
                    tripleShot.add(arrow.copy());
                    tripleShot.add(arrow.copy());
                    tripleShot.add(arrow.copy());
                    return tripleShot;
                }
            }
        }
        return projectiles;
    }
}
