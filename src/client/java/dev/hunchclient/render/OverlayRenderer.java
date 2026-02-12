/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.fabricmc.api.EnvType
 *  net.fabricmc.api.Environment
 */
package dev.hunchclient.render;

import dev.hunchclient.HunchClient;
import dev.hunchclient.module.Module;
import dev.hunchclient.module.ModuleManager;
import dev.hunchclient.module.OverlayRenderModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
public final class OverlayRenderer {
    private OverlayRenderer() {
    }

    public static void renderOverlays() {
        ModuleManager manager = ModuleManager.getInstance();
        for (Module module : manager.getModules()) {
            if (!module.isEnabled() || !(module instanceof OverlayRenderModule)) continue;
            OverlayRenderModule overlay = (OverlayRenderModule)((Object)module);
            try {
                overlay.renderOverlay();
            }
            catch (Exception e) {
                HunchClient.LOGGER.error("Overlay render error in {}: {}", (Object)module.getName(), (Object)e.getMessage());
            }
        }
    }
}

