package dev.hunchclient.render;

import dev.hunchclient.render.primitive.PrimitiveCollector;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

/**
 * Callback for extracting rendering primitives
 * Used by SecretRoutesModule for path rendering
 */
@FunctionalInterface
public interface WorldRenderExtractionCallback {
    Event<WorldRenderExtractionCallback> EVENT = EventFactory.createArrayBacked(
        WorldRenderExtractionCallback.class,
        (listeners) -> (collector) -> {
            for (WorldRenderExtractionCallback listener : listeners) {
                listener.onExtract(collector);
            }
        }
    );

    void onExtract(PrimitiveCollector collector);
}
