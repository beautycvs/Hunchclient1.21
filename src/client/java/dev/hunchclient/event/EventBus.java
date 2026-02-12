package dev.hunchclient.event;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Simple event bus for handling custom events
 */
public class EventBus {
    private static final EventBus INSTANCE = new EventBus();

    private final List<Consumer<BlockChangeEvent>> blockChangeListeners = new ArrayList<>();

    public static EventBus getInstance() {
        return INSTANCE;
    }

    public void registerBlockChangeListener(Consumer<BlockChangeEvent> listener) {
        blockChangeListeners.add(listener);
    }

    public void unregisterBlockChangeListener(Consumer<BlockChangeEvent> listener) {
        blockChangeListeners.remove(listener);
    }

    public void postBlockChange(BlockChangeEvent event) {
        for (Consumer<BlockChangeEvent> listener : blockChangeListeners) {
            listener.accept(event);
        }
    }
}
