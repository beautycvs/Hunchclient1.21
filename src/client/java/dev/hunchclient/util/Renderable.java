package dev.hunchclient.util;

import dev.hunchclient.render.RenderContext;

/**
 * Interface for objects that need to render
 * Adapted from Skyblocker PrimitiveCollector system
 */
public interface Renderable {
    void extractRendering(RenderContext context);
}
