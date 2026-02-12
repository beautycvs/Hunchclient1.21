package dev.hunchclient.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.hunchclient.mixin.accessor.LayerRenderStateAccessor;
import dev.hunchclient.bridge.ModuleBridge;
import dev.hunchclient.bridge.module.IViewmodelOverlay;
import dev.hunchclient.render.FirstPersonRenderContext;
import dev.hunchclient.render.GalaxyGlintRenderLayer;
import dev.hunchclient.render.GalaxyRenderState;
import dev.hunchclient.render.GalaxyStencilRenderer;
import dev.hunchclient.render.ItemOverlayRenderLayer;
import dev.hunchclient.render.OverlayTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemDisplayContext;

@Mixin(ItemStackRenderState.LayerRenderState.class)
public abstract class ItemRenderStateLayerMixin {

    // Track if we're rendering a first-person item
    private static final ThreadLocal<Boolean> isFirstPerson = ThreadLocal.withInitial(() -> false);

    /**
     * Set first-person rendering flag (called via Invoker from HeldItemRendererMixin)
     */
    private static void setFirstPersonRendering(boolean firstPerson) {
        isFirstPerson.set(firstPerson);
    }

    /**
     * Get first-person rendering flag (called via Invoker from self)
     */
    private static boolean isFirstPersonRendering() {
        return isFirstPerson.get();
    }

    /**
     * STENCIL MODE - STEP 1: Enable stencil writing before item renders
     */
    @Inject(
        method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void hunchclient$stencilBeforeRender(PoseStack matrices, SubmitNodeCollector submitNodeCollector, int i, int j, int k, CallbackInfo ci) {
        // DISABLED: Galaxy rendering system is not fully implemented yet
        return;

        /* ORIGINAL CODE - DISABLED
        if (!GalaxyRenderState.isUsingStencilMode()) {
            return;
        }

        int currentLayer = GalaxyRenderState.getLayerCount();
        GalaxyRenderState.incrementLayerCount();

        System.out.println("[ItemRenderStateLayerMixin] Stencil mode - Layer " + currentLayer);

        // Skip all layers except layer 0
        if (currentLayer > 0) {
            System.out.println("[ItemRenderStateLayerMixin] ❌ SKIPPING layer " + currentLayer);
            ci.cancel();
            return;
        }

        // Layer 0: Enable stencil writing
        System.out.println("[ItemRenderStateLayerMixin] ✅ Layer 0 - ENABLING STENCIL WRITE MODE");
        GalaxyStencilRenderer.beginStencilWrite();
        */
    }

    /**
     * SECOND-PASS OVERLAY RENDERING - DISABLED
     *
     * Now using ViewmodelOverlayRenderer snapshot comparison approach instead.
     * The snapshot comparison runs after ALL hand rendering completes and uses
     * pixel difference detection to apply the overlay only where hands are.
     *
     * This per-layer approach had issues with:
     * - Incorrect transforms (second item at wrong position)
     * - Custom RenderPipeline/shaders not working in 1.21+
     */
    /* DISABLED - Using ViewmodelOverlayRenderer.captureWorldSnapshot() + applyOverlayPostProcess() instead
    private static long lastDebugTime = 0;

    @Inject(
        method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V",
        at = @At("RETURN")
    )
    private void hunchclient$renderOverlayPass(PoseStack matrices, SubmitNodeCollector submitNodeCollector, int light, int overlay, int seed, CallbackInfo ci) {
        // ... disabled ...
    }
    */

    /* DISABLED - Galaxy glint approach causes black screen
    @ModifyArg(
        method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitItem(...)"),
        index = 8
    )
    private ItemStackRenderState.FoilType hunchclient$forceGlintForFirstPerson(ItemStackRenderState.FoilType originalFoilType) {
        return originalFoilType;
    }
    */


    /**
     * Modify UVs based on 3D position of vertices
     * This preserves item shape because each vertex gets unique UVs based on its 3D location!
     */
    @ModifyArg(
        method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitItem(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;III[ILjava/util/List;Lnet/minecraft/client/renderer/RenderType;Lnet/minecraft/client/renderer/item/ItemStackRenderState$FoilType;)V"),
        index = 6
    )
    private List<BakedQuad> hunchclient$modifyQuadsWithPositionBasedUVs(List<BakedQuad> originalQuads) {
        // DISABLED: Galaxy rendering system is not fully implemented yet
        return originalQuads;

        /* ORIGINAL CODE - DISABLED
        if (!GalaxyRenderState.isReplacingTexture() || originalQuads == null || originalQuads.isEmpty()) {
            return originalQuads;
        }

        // NEUE STRATEGIE: Behalte die original UVs! Ersetze nur die Textur!
        // Das verhindert den Sandwich-Effekt weil Front und Back die gleichen UVs haben
        System.out.println("[ItemRenderStateLayerMixin] KEEPING ORIGINAL UVs - just replacing texture - " + originalQuads.size() + " quads");
        return originalQuads;
        */
    }

    private List<BakedQuad> createTriplanarMappedQuads(List<BakedQuad> originalQuads) {
        final int VERTEX_SIZE = 8;
        final int X_OFFSET = 0;
        final int Y_OFFSET = 1;
        final int Z_OFFSET = 2;
        final int U_OFFSET = 4;
        final int V_OFFSET = 5;

        List<BakedQuad> result = new java.util.ArrayList<>();

        System.out.println("[ItemRenderStateLayerMixin] Processing " + originalQuads.size() + " quads with triplanar mapping");

        // TEST: Try filtering the OPPOSITE directions!
        // Maybe SOUTH, UP, EAST are the back faces?

        for (BakedQuad quad : originalQuads) {
            net.minecraft.core.Direction face = quad.direction();

            System.out.println("[ItemRenderStateLayerMixin] Testing quad with face: " + face);

            // Try filtering OPPOSITE directions
            if (face == net.minecraft.core.Direction.SOUTH ||
                face == net.minecraft.core.Direction.UP ||
                face == net.minecraft.core.Direction.EAST) {
                System.out.println("[ItemRenderStateLayerMixin] SKIPPING (TEST): " + face);
                continue;
            }

            System.out.println("[ItemRenderStateLayerMixin] KEEPING (TEST): " + face);
            int[] data = quad.vertices().clone();

            for (int i = 0; i < 4; i++) {
                int offset = i * VERTEX_SIZE;

                // Get 3D position
                float x = Float.intBitsToFloat(data[offset + X_OFFSET]);
                float y = Float.intBitsToFloat(data[offset + Y_OFFSET]);
                float z = Float.intBitsToFloat(data[offset + Z_OFFSET]);

                // NO SCALING - SCALE=1.0f to prevent tiling!
                // Item coordinates are 0-1, we map them directly to 0-1 UV space
                final float SCALE = 1.0f;

                float u, v;

                if (face == net.minecraft.core.Direction.UP || face == net.minecraft.core.Direction.DOWN) {
                    // Top/Bottom faces: use X and Z
                    u = x * SCALE;
                    v = z * SCALE;
                } else if (face == net.minecraft.core.Direction.NORTH || face == net.minecraft.core.Direction.SOUTH) {
                    // North/South faces: use X and Y
                    u = x * SCALE;
                    v = y * SCALE;
                } else {
                    // East/West faces: use Z and Y
                    u = z * SCALE;
                    v = y * SCALE;
                }

                // Add parallax offset
                float yaw = GalaxyRenderState.getCameraYaw();
                float pitch = GalaxyRenderState.getCameraPitch();
                u += (yaw / 360.0f) * 0.1f;
                v += (pitch / 180.0f) * 0.1f;

                // Write new UVs
                data[offset + U_OFFSET] = Float.floatToRawIntBits(u);
                data[offset + V_OFFSET] = Float.floatToRawIntBits(v);
            }

            result.add(new BakedQuad(data, quad.tintIndex(), quad.direction(), quad.sprite(), quad.shade(), quad.lightEmission()));
        }

        System.out.println("[ItemRenderStateLayerMixin] Triplanar mapping complete - " + result.size() + " quads");
        return result;
    }

    private List<BakedQuad> createPositionBasedUVQuads(List<BakedQuad> originalQuads) {
        final int VERTEX_SIZE = 8;
        final int X_OFFSET = 0;
        final int Y_OFFSET = 1;
        final int Z_OFFSET = 2;
        final int U_OFFSET = 4;
        final int V_OFFSET = 5;

        // Get parallax offset
        float yaw = GalaxyRenderState.getCameraYaw();
        float pitch = GalaxyRenderState.getCameraPitch();
        float parallaxU = (yaw / 360.0f) * 0.2f;
        float parallaxV = (pitch / 180.0f) * 0.2f;

        List<BakedQuad> result = new java.util.ArrayList<>();
        for (BakedQuad quad : originalQuads) {
            int[] data = quad.vertices().clone();

            for (int i = 0; i < 4; i++) {
                int offset = i * VERTEX_SIZE;

                // Get 3D position
                float x = Float.intBitsToFloat(data[offset + X_OFFSET]);
                float y = Float.intBitsToFloat(data[offset + Y_OFFSET]);
                float z = Float.intBitsToFloat(data[offset + Z_OFFSET]);

                // Calculate UVs based on 3D position
                // Use XY for U, YZ for V (simple projection)
                // Scale and offset to get nice galaxy coverage
                float u = (x + y) * 0.5f + 0.5f + parallaxU;
                float v = (y + z) * 0.5f + 0.5f + parallaxV;

                // Write new UVs
                data[offset + U_OFFSET] = Float.floatToRawIntBits(u);
                data[offset + V_OFFSET] = Float.floatToRawIntBits(v);
            }

            result.add(new BakedQuad(data, quad.tintIndex(), quad.direction(), quad.sprite(), quad.shade(), quad.lightEmission()));
        }

        return result;
    }

}
