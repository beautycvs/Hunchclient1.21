/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.fabricmc.api.EnvType
 *  net.fabricmc.api.Environment
 */
package dev.hunchclient.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
public class RenderTimeCache {
    private static long cachedFrameTime = 0L;
    private static long lastUpdateFrame = 0L;

    public static void updateFrameTime() {
        long currentFrame = System.nanoTime() / 1000000L;
        if (currentFrame != lastUpdateFrame) {
            cachedFrameTime = System.currentTimeMillis();
            lastUpdateFrame = currentFrame;
        }
    }

    public static long getFrameTime() {
        return cachedFrameTime;
    }
}

