package dev.hunchclient.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.hunchclient.bridge.ModuleBridge;
import dev.hunchclient.bridge.RenderBridge;
import dev.hunchclient.bridge.module.IViewmodelOverlay;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;

/**
 * Mixin to intercept item rendering and overlay texture on item geometry.
 * Filters out large overlay quads and only textures actual item geometry.
 */
@Mixin(SubmitNodeCollection.class)
public abstract class BatchingRenderCommandQueueMixin {

    @Unique
    private static boolean hunchclient$shouldReplaceItem() {
        IViewmodelOverlay module = ModuleBridge.viewmodelOverlay();
        // Skip legacy quad overlay when the new FBO-based overlay is capturing
        if (RenderBridge.isOverlayCapturing()) {
            return false;
        }
        return module != null && module.isEnabled() && RenderBridge.hasOverlayTexture()
            && module.getOverlayOpacity() > 0.001f;
    }

    /**
     * Check if a quad is a flat overlay (all vertices on same Z plane)
     * Overlay quads have very little Z variation, real item quads have 3D depth
     */
    @Unique
    private static boolean hunchclient$isOverlayQuad(BakedQuad quad) {
        int[] vertexData = quad.vertices();

        float z0 = Float.intBitsToFloat(vertexData[2]);
        float z1 = Float.intBitsToFloat(vertexData[10]);
        float z2 = Float.intBitsToFloat(vertexData[18]);
        float z3 = Float.intBitsToFloat(vertexData[26]);

        float minZ = Math.min(Math.min(z0, z1), Math.min(z2, z3));
        float maxZ = Math.max(Math.max(z0, z1), Math.max(z2, z3));

        // If Z range is very small, it's a flat overlay quad
        // Real item geometry has depth variation
        return (maxZ - minZ) < 0.001f;
    }

    /**
     * Check if quad is too large (overlay quads are usually bigger)
     */
    @Unique
    private static float hunchclient$getQuadSize(BakedQuad quad) {
        int[] vertexData = quad.vertices();

        float x0 = Float.intBitsToFloat(vertexData[0]);
        float y0 = Float.intBitsToFloat(vertexData[1]);
        float x1 = Float.intBitsToFloat(vertexData[8]);
        float y1 = Float.intBitsToFloat(vertexData[9]);
        float x2 = Float.intBitsToFloat(vertexData[16]);
        float y2 = Float.intBitsToFloat(vertexData[17]);
        float x3 = Float.intBitsToFloat(vertexData[24]);
        float y3 = Float.intBitsToFloat(vertexData[25]);

        float minX = Math.min(Math.min(x0, x1), Math.min(x2, x3));
        float maxX = Math.max(Math.max(x0, x1), Math.max(x2, x3));
        float minY = Math.min(Math.min(y0, y1), Math.min(y2, y3));
        float maxY = Math.max(Math.max(y0, y1), Math.max(y2, y3));

        return (maxX - minX) * (maxY - minY);
    }

    // DISABLED - Using FBO-based ViewmodelOverlayRenderer instead
    // This legacy quad-overlay approach conflicts with the FBO system
    /*
    @Inject(
        method = "submitItem",
        at = @At("TAIL")
    )
    */
    private void hunchclient$overlayTextureOnItem(
        PoseStack matrices,
        ItemDisplayContext displayContext,
        int light,
        int overlay,
        int outlineColors,
        int[] tintLayers,
        List<BakedQuad> quads,
        RenderType renderLayer,
        ItemStackRenderState.FoilType glintType,
        CallbackInfo ci
    ) {
        // DISABLED - causes conflicts with FBO-based overlay
        if (true) return;

        if (displayContext != ItemDisplayContext.FIRST_PERSON_RIGHT_HAND &&
            displayContext != ItemDisplayContext.FIRST_PERSON_LEFT_HAND) {
            return;
        }

        if (!hunchclient$shouldReplaceItem()) {
            return;
        }

        try {
            IViewmodelOverlay overlayModule = ModuleBridge.viewmodelOverlay();
            float opacity = overlayModule != null ? overlayModule.getOverlayOpacity() : 1.0f;
            if (opacity <= 0.001f) {
                return;
            }
            int overlayAlpha = Math.min(255, Math.max(0, Math.round(opacity * 255.0f)));

            Minecraft client = Minecraft.getInstance();
            ResourceLocation overlayTexture = RenderBridge.getOverlayTextureId();

            if (overlayTexture == null || quads.isEmpty()) {
                return;
            }

            // Filter quads - exclude flat overlay quads and very large quads
            List<BakedQuad> itemQuads = new ArrayList<>();
            for (BakedQuad quad : quads) {
                // Skip flat overlay quads (no Z depth)
                if (hunchclient$isOverlayQuad(quad)) {
                    continue;
                }
                // Skip very large quads (GUI elements)
                float size = hunchclient$getQuadSize(quad);
                if (size > 1.0f) {
                    continue;
                }
                itemQuads.add(quad);
            }

            if (itemQuads.isEmpty()) {
                return;
            }

            PoseStack.Pose matrixEntry = matrices.last();
            Matrix4f posMatrix = matrixEntry.pose();

            // Calculate transformed bounding box from filtered quads
            float minTX = Float.MAX_VALUE, maxTX = -Float.MAX_VALUE;
            float minTY = Float.MAX_VALUE, maxTY = -Float.MAX_VALUE;

            Vector4f tempVec = new Vector4f();

            for (BakedQuad quad : itemQuads) {
                int[] vertexData = quad.vertices();
                for (int i = 0; i < 4; i++) {
                    int offset = i * 8;
                    float x = Float.intBitsToFloat(vertexData[offset]);
                    float y = Float.intBitsToFloat(vertexData[offset + 1]);
                    float z = Float.intBitsToFloat(vertexData[offset + 2]);

                    tempVec.set(x, y, z, 1.0f);
                    tempVec.mul(posMatrix);

                    minTX = Math.min(minTX, tempVec.x);
                    maxTX = Math.max(maxTX, tempVec.x);
                    minTY = Math.min(minTY, tempVec.y);
                    maxTY = Math.max(maxTY, tempVec.y);
                }
            }

            float rangeTX = maxTX - minTX;
            float rangeTY = maxTY - minTY;

            // Render overlay on item quads only
            RenderType texturedLayer = RenderType.entityTranslucentEmissive(overlayTexture);
            MultiBufferSource.BufferSource immediate = client.renderBuffers().bufferSource();
            VertexConsumer buffer = immediate.getBuffer(texturedLayer);

            float zOffset = 0.001f;

            for (BakedQuad quad : itemQuads) {
                int[] vertexData = quad.vertices();

                for (int i = 0; i < 4; i++) {
                    int offset = i * 8;
                    float x = Float.intBitsToFloat(vertexData[offset]);
                    float y = Float.intBitsToFloat(vertexData[offset + 1]);
                    float z = Float.intBitsToFloat(vertexData[offset + 2]);

                    int packedNormal = vertexData[offset + 7];
                    float nx = (byte)(packedNormal & 0xFF) / 127.0f;
                    float ny = (byte)((packedNormal >> 8) & 0xFF) / 127.0f;
                    float nz = (byte)((packedNormal >> 16) & 0xFF) / 127.0f;

                    float ox = x + nx * zOffset;
                    float oy = y + ny * zOffset;
                    float oz = z + nz * zOffset;

                    tempVec.set(x, y, z, 1.0f);
                    tempVec.mul(posMatrix);

                    float u = rangeTX > 0.001f ? (tempVec.x - minTX) / rangeTX : 0.5f;
                    float v = rangeTY > 0.001f ? 1.0f - (tempVec.y - minTY) / rangeTY : 0.5f;

                    buffer.addVertex(matrixEntry, ox, oy, oz)
                        .setColor(255, 255, 255, overlayAlpha)
                        .setUv(u, v)
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(0xF000F0)
                        .setNormal(matrixEntry, nx, ny, nz);
                }
            }

            immediate.endBatch(texturedLayer);

        } catch (Exception e) {
            System.err.println("[BatchingRenderCommandQueueMixin] Error: " + e.getMessage());
        }
    }
}
