package dev.hunchclient.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.hunchclient.bridge.ModuleBridge;
import dev.hunchclient.bridge.module.IAnimations;
import dev.hunchclient.bridge.module.IViewmodelOverlay;
import dev.hunchclient.render.FirstPersonRenderContext;
import dev.hunchclient.render.GalaxyGlintRenderLayer;
import dev.hunchclient.render.OverlayTextureManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(ItemInHandRenderer.class)
public abstract class HeldItemRendererMixin {

    @Shadow @Final private Minecraft minecraft;

    private static float hunchclient$currentSwingProgress = 0.0f;

    // Flag to prevent recursive glow rendering
    private static boolean hunchclient$isRenderingGlow = false;

    private IAnimations hunchclient$module() {
        IAnimations module = ModuleBridge.animations();
        return module != null && module.isEnabled() ? module : null;
    }

    /**
     * DISABLED: FBO-switching approach doesn't work in 1.21+ deferred rendering
     *
     * In 1.21+, renderHandsWithItems() only QUEUES commands to SubmitNodeCollector.
     * The actual GPU execution happens later in FrameGraphBuilder.execute().
     * FBO switching at HEAD/RETURN affects nothing because no GPU work happens here.
     *
     * Solution: Use ItemRenderStateLayerMixin which adds a second render pass
     * with an overlay shader at the correct point in the deferred pipeline.
     */
    @Inject(
        method = "renderHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/player/LocalPlayer;I)V",
        at = @At("HEAD")
    )
    private void hunchclient$setFirstPersonContext(
        float tickDelta,
        PoseStack matrices,
        SubmitNodeCollector submitNodeCollector,
        LocalPlayer player,
        int light,
        CallbackInfo ci
    ) {
        // Set the context flag for ItemRenderStateLayerMixin
        IViewmodelOverlay overlayModule = ModuleBridge.viewmodelOverlay();
        if (overlayModule != null && overlayModule.isEnabled()) {
            FirstPersonRenderContext.setFirstPerson(true);
            // Galaxy glint approach disabled - causes black screen
            // GalaxyGlintRenderLayer.setRenderingFirstPerson(true);
        }
    }

    /**
     * Reset first-person context after hand rendering completes
     */
    @Inject(
        method = "renderHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/player/LocalPlayer;I)V",
        at = @At("RETURN")
    )
    private void hunchclient$resetFirstPersonContext(
        float tickDelta,
        PoseStack matrices,
        SubmitNodeCollector submitNodeCollector,
        LocalPlayer player,
        int light,
        CallbackInfo ci
    ) {
        FirstPersonRenderContext.setFirstPerson(false);
        // Galaxy glint approach disabled
        // GalaxyGlintRenderLayer.setRenderingFirstPerson(false);
    }

    /**
     * Initialize first-person context flag and item glow for first-person items.
     * NOTE: Stencil-based overlay is now handled in ItemCommandRendererMixin
     * which hooks into the actual deferred rendering execution point.
     */
    @Inject(
        method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
        at = @At("HEAD")
    )
    private void hunchclient$initOverlayRendering(
        LivingEntity entity,
        ItemStack stack,
        ItemDisplayContext context,
        PoseStack matrices,
        SubmitNodeCollector submitNodeCollector,
        int light,
        CallbackInfo ci
    ) {
        // Set first-person flag AND display context for ItemRenderStateLayerMixin
        boolean isFirstPerson = (context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND ||
                                 context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND);

        dev.hunchclient.render.FirstPersonRenderContext.setFirstPerson(isFirstPerson);
        dev.hunchclient.render.FirstPersonRenderContext.setDisplayContext(context);

        // Check if this is a first-person item
        if (!isFirstPerson) {
            return;
        }

        IViewmodelOverlay overlayModule = ModuleBridge.viewmodelOverlay();

        // Check if item glow should be enabled
        if (overlayModule != null && overlayModule.isEnabled() && overlayModule.isItemGlowEnabled()) {
            // Enable item glow rendering flag
            dev.hunchclient.render.ItemGlowRenderer.beginCapture();
        }
    }

    /**
     * Reset first-person flag and finalize item glow rendering.
     * NOTE: Stencil-based overlay is now handled in ItemCommandRendererMixin.
     */
    @Inject(
        method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
        at = @At("RETURN")
    )
    private void hunchclient$resetGalaxyFlag(
        LivingEntity entity,
        ItemStack stack,
        ItemDisplayContext context,
        PoseStack matrices,
        SubmitNodeCollector submitNodeCollector,
        int light,
        CallbackInfo ci
    ) {
        // Reset first-person flag
        dev.hunchclient.render.FirstPersonRenderContext.reset();

        // Finalize item glow rendering
        if (dev.hunchclient.render.ItemGlowRenderer.isCapturing()) {
            dev.hunchclient.render.ItemGlowRenderer.endCaptureAndRender();
        }
    }

    // NOTE: Viewmodel overlay is now handled via per-layer rendering in ItemRenderStateLayerMixin
    // The post-process approach affected the entire screen instead of just hands.
    // Using second-pass rendering similar to Iris gbuffers_hand.

    // ------------------------------------------------------------------------
    // Track swing progress to differentiate between swing and swap
    // ------------------------------------------------------------------------

    @Inject(method = "swingArm", at = @At("HEAD"))
    private void hunchclient$captureSwingProgress(float swingProgress, float equipProgress, PoseStack matrices, int armX, HumanoidArm arm, CallbackInfo ci) {
        hunchclient$currentSwingProgress = swingProgress;
    }

    @Inject(method = "swingArm", at = @At("RETURN"))
    private void hunchclient$resetSwingProgress(CallbackInfo ci) {
        hunchclient$currentSwingProgress = 0.0f;
    }

    // ------------------------------------------------------------------------
    // Equip offset adjustments (X/Y/Z sliders)
    // ------------------------------------------------------------------------

    @ModifyConstant(method = "applyItemArmTransform", constant = @org.spongepowered.asm.mixin.injection.Constant(floatValue = -0.6f))
    private float hunchclient$modifyEquipBobbing(float original) {
        IAnimations module = hunchclient$module();
        if (module == null) {
            return original;
        }

        // Cancel bobbing during active swing (swingProgress > 0)
        if (hunchclient$currentSwingProgress > 0.0f) {
            return 0.0f;
        }

        // Apply bobbing speed multiplier for equip/swap animation
        return original * module.getEquipBobbingSpeed();
    }

    // TODO: resetEquipProgress no longer exists in 1.21.10 Mojang mappings
    // The "No Equip Reset" feature needs a new implementation approach
    // Old method: resetEquipProgress(InteractionHand) - now handled differently in tick()
    // @Inject(method = "resetEquipProgress(Lnet/minecraft/world/InteractionHand;)V", at = @At("HEAD"), cancellable = true)
    // private void hunchclient$cancelEquipReset(InteractionHand hand, CallbackInfo ci) {
    //     IAnimations module = hunchclient$module();
    //     if (module != null && module.isNoEquipReset()) {
    //         ci.cancel();
    //     }
    // }

    @Inject(method = "applyItemArmTransform", at = @At("TAIL"))
    private void hunchclient$adjustEquipOffset(PoseStack matrices, HumanoidArm arm, float equipProgress, CallbackInfo ci) {
        IAnimations module = hunchclient$module();
        if (module == null) {
            return;
        }

        int direction = arm == HumanoidArm.RIGHT ? 1 : -1;
        float offsetX = module.getOffsetX();
        float offsetY = module.getOffsetY();
        float offsetZ = module.getOffsetZ();

        if (Math.abs(offsetX) > 1.0E-4f || Math.abs(offsetY) > 1.0E-4f || Math.abs(offsetZ) > 1.0E-4f) {
            matrices.translate(direction * 0.56f * offsetX, 0.52f * offsetY, -0.72f * offsetZ);
        }
    }

    // ------------------------------------------------------------------------
    // Translation scaling in swingArm (swing animation translation)
    // ------------------------------------------------------------------------

    @ModifyArgs(method = "swingArm", at = @At(
        value = "INVOKE",
        target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"
    ))
    private void hunchclient$scaleSwingTranslate(Args args,
                                                 float swingProgress,
                                                 float equipProgress,
                                                 PoseStack matrices,
                                                 int armX,
                                                 HumanoidArm arm) {
        IAnimations module = hunchclient$module();
        if (module == null) {
            return;
        }

        LocalPlayer player = this.minecraft.player;
        if (player != null && module.shouldZeroSwing(player)) {
            args.set(0, 0.0f);
            args.set(1, 0.0f);
            args.set(2, 0.0f);
            return;
        }

        float scale = module.getSwingTranslationScale();
        if (Math.abs(scale - 1.0f) > 1.0E-4f) {
            args.set(0, ((Float) args.get(0)) * scale);
            args.set(1, ((Float) args.get(1)) * scale);
            args.set(2, ((Float) args.get(2)) * scale);
        }
    }

    // ------------------------------------------------------------------------
    // Scale and rotations in applyItemArmAttackTransform (swing rotation)
    // NOTE: In 1.21.10, rotation constants moved from swingArm to applyItemArmAttackTransform
    // ------------------------------------------------------------------------

    @ModifyConstant(method = "applyItemArmAttackTransform", constant = @org.spongepowered.asm.mixin.injection.Constant(floatValue = -20.0f), expect = 2)
    private float hunchclient$scaleSwingRotation20(float original) {
        IAnimations module = hunchclient$module();
        if (module == null) {
            return original;
        }
        LocalPlayer player = this.minecraft.player;
        if (player != null && module.shouldZeroSwing(player)) {
            return 0.0f;
        }
        return original * module.getSwingRotationScale();
    }

    @ModifyConstant(method = "applyItemArmAttackTransform", constant = @org.spongepowered.asm.mixin.injection.Constant(floatValue = -80.0f))
    private float hunchclient$scaleSwingRotation80(float original) {
        IAnimations module = hunchclient$module();
        if (module == null) {
            return original;
        }
        LocalPlayer player = this.minecraft.player;
        if (player != null && module.shouldZeroSwing(player)) {
            return 0.0f;
        }
        return original * module.getSwingRotationScale();
    }

    @ModifyConstant(method = "applyItemArmAttackTransform", constant = @org.spongepowered.asm.mixin.injection.Constant(floatValue = -45.0f))
    private float hunchclient$scaleSwingRotation45(float original) {
        IAnimations module = hunchclient$module();
        if (module == null) {
            return original;
        }
        LocalPlayer player = this.minecraft.player;
        if (player != null && module.shouldZeroSwing(player)) {
            return 0.0f;
        }
        return original * module.getSwingRotationScale();
    }

    @ModifyConstant(method = "applyItemArmAttackTransform", constant = @org.spongepowered.asm.mixin.injection.Constant(floatValue = 45.0f))
    private float hunchclient$scaleSwingRotation45Positive(float original) {
        IAnimations module = hunchclient$module();
        if (module == null) {
            return original;
        }
        LocalPlayer player = this.minecraft.player;
        if (player != null && module.shouldZeroSwing(player)) {
            return 0.0f;
        }
        return original * module.getSwingRotationScale();
    }

    // ------------------------------------------------------------------------
    // Apply final rotation/scale before rendering the held item
    // ------------------------------------------------------------------------

    /**
     * Apply custom scaling directly in the shared renderItem path so every
     * first-person item (regardless of the special-case branch taken earlier)
     * inherits the Animations size configuration.
     */
    @Inject(
        method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
        at = @At("HEAD")
    )
    private void hunchclient$scaleFirstPersonItem(LivingEntity entity,
                                                  ItemStack stack,
                                                  ItemDisplayContext context,
                                                  PoseStack matrices,
                                                  SubmitNodeCollector submitNodeCollector,
                                                  int light,
                                                  CallbackInfo ci) {
        IAnimations module = hunchclient$module();
        if (module == null) {
            return;
        }

        if (context != ItemDisplayContext.FIRST_PERSON_RIGHT_HAND && context != ItemDisplayContext.FIRST_PERSON_LEFT_HAND) {
            return;
        }

        float scale = module.getScaleMultiplier();
        if (Math.abs(scale - 1.0f) > 1.0E-4f) {
            matrices.scale(scale, scale, scale);
        }
    }



    /**
     * Apply rotation transformations in applyEquipOffset.
     * At this stage, the item is positioned but not yet rotated for swing.
     * We inject at TAIL to apply rotations after vanilla position offsets.
     */
    @Inject(method = "applyItemArmTransform", at = @At("TAIL"))
    private void hunchclient$applyRotationsInEquip(PoseStack matrices, HumanoidArm arm, float equipProgress, CallbackInfo ci) {
        IAnimations module = hunchclient$module();
        if (module == null) {
            return;
        }

        int direction = arm == HumanoidArm.RIGHT ? 1 : -1;
        float yaw = module.getYaw();
        float pitchAngle = module.getPitch();
        float roll = module.getRoll();

        // Apply rotations around the current origin
        // These are applied after positioning, so they rotate in-place
        if (Math.abs(yaw) > 1.0E-4f) {
            matrices.mulPose(Axis.YP.rotationDegrees(yaw));
        }

        if (Math.abs(pitchAngle) > 1.0E-4f) {
            matrices.mulPose(Axis.XP.rotationDegrees(pitchAngle));
        }

        if (Math.abs(roll) > 1.0E-4f) {
            matrices.mulPose(Axis.ZP.rotationDegrees(roll * direction));
        }
    }

}
