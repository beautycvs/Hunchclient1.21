package dev.hunchclient.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;

/**
 * Custom RenderPhase for binding the overlay texture to Sampler1
 * This ensures the shader can access the custom overlay texture
 */
public class OverlayTexturePhase extends RenderStateShard.EmptyTextureStateShard {

    private final ResourceLocation overlayTextureId;
    private final ResourceLocation itemAtlasTextureId;

    public OverlayTexturePhase(ResourceLocation overlayTextureId, ResourceLocation itemAtlasTextureId) {
        super(
            // Setup action - bind BOTH textures
            () -> {
                TextureManager textureManager = Minecraft.getInstance().getTextureManager();

                // Bind item atlas texture to slot 0 (Sampler0 in shader)
                if (itemAtlasTextureId != null) {
                    AbstractTexture itemTexture = textureManager.getTexture(itemAtlasTextureId);
                    if (itemTexture != null && itemTexture.getTextureView() != null) {
                        RenderSystem.setShaderTexture(0, itemTexture.getTextureView());
                    }
                }

                // Bind overlay texture to slot 1 (Sampler1 in shader)
                // Shader slots: 0=Sampler0 (item), 1=Sampler1 (overlay), 2=Sampler2 (lightmap)
                if (overlayTextureId != null) {
                    AbstractTexture overlayTexture = textureManager.getTexture(overlayTextureId);
                    if (overlayTexture != null && overlayTexture.getTextureView() != null) {
                        RenderSystem.setShaderTexture(1, overlayTexture.getTextureView());
                    }
                }
            },
            // Teardown action - unbind textures
            () -> {
                RenderSystem.setShaderTexture(0, null);
                RenderSystem.setShaderTexture(1, null);
            }
        );
        this.overlayTextureId = overlayTextureId;
        this.itemAtlasTextureId = itemAtlasTextureId;
    }

    /**
     * Create a phase that dynamically gets the current overlay texture
     */
    public static OverlayTexturePhase create(ResourceLocation itemAtlasTextureId) {
        ResourceLocation overlayTextureId = OverlayTextureManager.getTextureId();
        return new OverlayTexturePhase(overlayTextureId, itemAtlasTextureId);
    }

    @Override
    public String toString() {
        return "OverlayTexturePhase{texture=" + overlayTextureId + ", itemAtlas=" + itemAtlasTextureId + "}";
    }
}
