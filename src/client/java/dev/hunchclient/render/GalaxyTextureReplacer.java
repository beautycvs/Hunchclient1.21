package dev.hunchclient.render;

import com.mojang.blaze3d.platform.NativeImage;
import dev.hunchclient.module.impl.ViewmodelOverlayModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Galaxy Texture Replacer
 *
 * Loads a galaxy PNG texture and provides functionality to bind it
 * with dynamic UV offsets based on camera movement (parallax effect).
 */
public class GalaxyTextureReplacer {

    private static final Logger LOGGER = LoggerFactory.getLogger(GalaxyTextureReplacer.class);
    private static final ResourceLocation GALAXY_TEXTURE_ID = ResourceLocation.fromNamespaceAndPath("hunchclient", "textures/galaxy.png");

    private static boolean initialized = false;
    private static DynamicTexture galaxyTexture;
    private static ResourceLocation registeredTextureId;

    // Animation state
    private static float animationTime = 0.0f;
    private static float lastPitch = 0.0f;
    private static float lastYaw = 0.0f;
    private static float smoothPitch = 0.0f;
    private static float smoothYaw = 0.0f;

    // UV offset for parallax
    private static float uvOffsetX = 0.0f;
    private static float uvOffsetY = 0.0f;
    private static float textureRotation = 0.0f;

    /**
     * Initialize the galaxy texture system
     * This loads the texture lazily when OpenGL is ready
     */
    public static void init() {
        System.out.println("[GalaxyTextureReplacer] init() called, initialized=" + initialized);

        if (initialized) {
            System.out.println("[GalaxyTextureReplacer] Already initialized, skipping");
            return;
        }

        System.out.println("[GalaxyTextureReplacer] Starting initialization...");

        try {
            Minecraft client = Minecraft.getInstance();
            System.out.println("[GalaxyTextureReplacer] MinecraftClient: " + client);

            // Check if RenderSystem is initialized
            try {
                com.mojang.blaze3d.systems.RenderSystem.getDevice();
                System.out.println("[GalaxyTextureReplacer] RenderSystem is ready!");
            } catch (IllegalStateException e) {
                System.out.println("[GalaxyTextureReplacer] RenderSystem not ready yet, will initialize on first use");
                // Don't mark as initialized yet, so it will be retried
                return;
            }

            // Load the galaxy PNG from resources
            InputStream stream = GalaxyTextureReplacer.class.getResourceAsStream("/assets/hunchclient/textures/galaxy.png");
            System.out.println("[GalaxyTextureReplacer] InputStream: " + stream);

            if (stream == null) {
                LOGGER.warn("Galaxy texture not found at /assets/hunchclient/textures/galaxy.png - using fallback");
                System.out.println("[GalaxyTextureReplacer] PNG not found, using fallback");
                createFallbackTexture();
            } else {
                // Load PNG into NativeImage
                System.out.println("[GalaxyTextureReplacer] Loading PNG...");
                NativeImage image = NativeImage.read(stream);
                galaxyTexture = new DynamicTexture(() -> "Galaxy Texture", image);

                // Register with TextureManager
                registeredTextureId = ResourceLocation.fromNamespaceAndPath("hunchclient", "dynamic/galaxy");
                System.out.println("[GalaxyTextureReplacer] Registering texture: " + registeredTextureId);
                client.getTextureManager().register(registeredTextureId, galaxyTexture);

                LOGGER.info("Galaxy texture loaded successfully: {}x{}", image.getWidth(), image.getHeight());
                System.out.println("[GalaxyTextureReplacer] Texture loaded: " + image.getWidth() + "x" + image.getHeight());
                stream.close();
            }

            initialized = true;
            System.out.println("[GalaxyTextureReplacer] Initialization complete! initialized=" + initialized);

        } catch (Exception e) {
            LOGGER.error("Failed to load galaxy texture", e);
            System.out.println("[GalaxyTextureReplacer] ERROR: " + e.getMessage());
            e.printStackTrace();
            // Don't mark as initialized so it can retry
        }
    }

    /**
     * Create a simple procedural galaxy texture as fallback
     */
    private static void createFallbackTexture() {
        try {
            int size = 256;
            NativeImage image = new NativeImage(size, size, true);

            // Generate simple gradient galaxy
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    float nx = (x - size / 2.0f) / (size / 2.0f);
                    float ny = (y - size / 2.0f) / (size / 2.0f);
                    float dist = (float) Math.sqrt(nx * nx + ny * ny);

                    // Create nebula-like pattern
                    float noise = (float) (Math.sin(x * 0.1) * Math.cos(y * 0.1));
                    float brightness = Math.max(0, 1.0f - dist) * (0.5f + noise * 0.5f);

                    // Purple/cyan gradient
                    int r = (int) (brightness * 128 + 50);
                    int g = (int) (brightness * 50 + 100);
                    int b = (int) (brightness * 200 + 55);
                    int a = 255;

                    // ABGR format for NativeImage
                    int color = (a << 24) | (b << 16) | (g << 8) | r;
                    image.setPixelABGR(x, y, color);
                }
            }

            galaxyTexture = new DynamicTexture(() -> "Galaxy Fallback", image);
            registeredTextureId = ResourceLocation.fromNamespaceAndPath("hunchclient", "dynamic/galaxy_fallback");
            Minecraft.getInstance().getTextureManager().register(registeredTextureId, galaxyTexture);

            LOGGER.info("Created fallback galaxy texture");

        } catch (Exception e) {
            LOGGER.error("Failed to create fallback texture", e);
        }
    }

    /**
     * Update animation state (called every tick)
     */
    public static void tick() {
        if (!initialized) {
            return;
        }

        ViewmodelOverlayModule module = ViewmodelOverlayModule.getInstance();
        if (module == null || !module.isEnabled()) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }

        // Update animation time
        animationTime += module.getAnimationSpeed() * 0.05f;

        // Get camera rotation
        float pitch = client.player.getXRot();
        float yaw = client.player.getYRot();

        // Smooth camera movement for parallax
        float smoothing = 0.3f;
        smoothPitch = smoothPitch * (1.0f - smoothing) + pitch * smoothing;
        smoothYaw = smoothYaw * (1.0f - smoothing) + yaw * smoothing;

        // Calculate UV offset based on camera movement (parallax effect)
        float parallaxIntensity = module.getParallaxIntensity();
        uvOffsetX = (smoothYaw / 360.0f) * parallaxIntensity;
        uvOffsetY = (smoothPitch / 180.0f) * parallaxIntensity;

        // Calculate rotation
        textureRotation += module.getRotationSpeed() * 0.5f;

        lastPitch = pitch;
        lastYaw = yaw;
    }

    /**
     * Get the registered texture identifier for binding
     * This is used by TextureManagerMixin to replace item textures
     */
    public static ResourceLocation getTextureId() {
        return registeredTextureId;
    }

    /**
     * Get current UV offset X for parallax
     */
    public static float getUvOffsetX() {
        return uvOffsetX;
    }

    /**
     * Get current UV offset Y for parallax
     */
    public static float getUvOffsetY() {
        return uvOffsetY;
    }

    /**
     * Get current texture rotation
     */
    public static float getRotation() {
        return textureRotation;
    }

    /**
     * Get animation time
     */
    public static float getAnimationTime() {
        return animationTime;
    }

    /**
     * Cleanup resources
     */
    public static void cleanup() {
        if (!initialized) {
            return;
        }

        if (galaxyTexture != null) {
            galaxyTexture.close();
            galaxyTexture = null;
        }

        if (registeredTextureId != null && Minecraft.getInstance() != null) {
            Minecraft.getInstance().getTextureManager().release(registeredTextureId);
            registeredTextureId = null;
        }

        initialized = false;
        animationTime = 0.0f;
        uvOffsetX = 0.0f;
        uvOffsetY = 0.0f;
        textureRotation = 0.0f;

        LOGGER.info("Galaxy texture cleaned up");
    }

    /**
     * Check if initialized
     */
    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * Update camera data from external source (e.g., HeldItemRendererMixin)
     */
    public static void updateCameraRotation(float pitch, float yaw) {
        lastPitch = pitch;
        lastYaw = yaw;
    }
}
