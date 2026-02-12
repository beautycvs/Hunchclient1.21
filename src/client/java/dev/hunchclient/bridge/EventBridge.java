package dev.hunchclient.bridge;

import meteordevelopment.orbit.IEventBus;

public final class EventBridge {

    private static IEventBus mainBus;
    private static dev.hunchclient.event.EventBus blockBus;

    private EventBridge() {}

    public static void init(IEventBus main, dev.hunchclient.event.EventBus block) {
        mainBus = main;
        blockBus = block;
    }

    public static void post(Object event) {
        if (mainBus != null) mainBus.post(event);
    }

    public static void postBlockChange(Object event) {
        if (blockBus != null) blockBus.postBlockChange((dev.hunchclient.event.BlockChangeEvent) event);
    }
}
