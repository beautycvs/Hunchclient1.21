package dev.hunchclient.module.impl.terminalsolver;

/**
 * Represents a queued click in high-ping mode.
 * Stores the slot, button, and timestamp for queue validation.
 */
public record QueuedClick(int slot, int button, long queuedAt) {

    /**
     * Check if this queued click has timed out
     * @param timeout timeout in milliseconds
     * @return true if this click has exceeded the timeout
     */
    public boolean isTimedOut(long timeout) {
        return System.currentTimeMillis() - queuedAt > timeout;
    }
}
