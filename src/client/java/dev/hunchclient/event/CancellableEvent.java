package dev.hunchclient.event;

import meteordevelopment.orbit.ICancellable;

/** Base cancellable event. */
public abstract class CancellableEvent implements ICancellable {
    private boolean cancelled = false;

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    public void cancel() {
        setCancelled(true);
    }
}
