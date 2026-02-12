package dev.hunchclient.render;

import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * Wrapper for VertexConsumer that modifies texture coordinates with parallax offset
 */
public class GalaxyVertexConsumer implements VertexConsumer {

    private final VertexConsumer delegate;
    private final float uvOffsetX;
    private final float uvOffsetY;

    public GalaxyVertexConsumer(VertexConsumer delegate) {
        this.delegate = delegate;
        this.uvOffsetX = GalaxyTextureReplacer.getUvOffsetX();
        this.uvOffsetY = GalaxyTextureReplacer.getUvOffsetY();
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        delegate.addVertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha) {
        delegate.setColor(red, green, blue, alpha);
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        // Apply parallax offset to UV coordinates
        delegate.setUv(u + uvOffsetX, v + uvOffsetY);
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        delegate.setUv1(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        delegate.setUv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        delegate.setNormal(x, y, z);
        return this;
    }
}
