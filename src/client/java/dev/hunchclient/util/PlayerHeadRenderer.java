package dev.hunchclient.util;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.component.ResolvableProfile;

/**
 * Utility to render player heads (skins) on HUD
 * 1:1 from Skyblocker's HudHelper.drawPlayerHead
 */
public class PlayerHeadRenderer {

    /**
     * Draws a player head without blocking or a default head if profile is not available immediately.
     * This fetches the profile so it will be available for future calls to this method.
     *
     * @param context DrawContext for rendering
     * @param x       X position (center of head)
     * @param y       Y position (center of head)
     * @param size    Size of the head (width/height)
     * @param uuid    Player UUID to get skin from
     */
    public static void drawPlayerHead(GuiGraphics context, int x, int y, int size, UUID uuid) {
        if (uuid == null) return;

        Minecraft mc = Minecraft.getInstance();

        // Use PlayerSkinCache for non-blocking texture fetch (1:1 from Skyblocker)
        PlayerSkin texture = mc.playerSkinRenderCache().lookup(ResolvableProfile.createUnresolved(uuid))
                .getNow(Optional.empty())
                .map(PlayerSkinRenderCache.RenderInfo::playerSkin)
                .orElseGet(() -> DefaultPlayerSkin.get(uuid));

        // Draw the player head using Minecraft's built-in PlayerSkinDrawer
        PlayerFaceRenderer.draw(context, texture, x, y, size);
    }
}
