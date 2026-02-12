package dev.hunchclient.render.postprocess;

import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.ResourceLocation;

/**
 * Manages the PostChain for shader ESP rendering
 */
public final class PostChainHolder {
    private static PostChain postChain;
    private static final ResourceLocation CHAIN_ID = ResourceLocation.fromNamespaceAndPath("hunchclient", "shaders/post/outline_esp");

    private PostChainHolder() {}

    public static PostChain get() {
        if (postChain == null) {
            Minecraft mc = Minecraft.getInstance();
            try {
                // Use ShaderLoader to properly load the post effect
                ShaderManager shaderLoader = mc.getShaderManager();
                Set<ResourceLocation> availableTargets = Set.of(PostChain.MAIN_TARGET_ID);
                postChain = shaderLoader.getPostChain(CHAIN_ID, availableTargets);
                System.out.println("[PostChainHolder] Successfully loaded post chain: " + CHAIN_ID);
            } catch (Exception e) {
                System.err.println("[PostChainHolder] Failed to load post chain: " + e.getMessage());
                e.printStackTrace();
            }
        }
        return postChain;
    }

    public static void clear() {
        postChain = null;
    }
}
