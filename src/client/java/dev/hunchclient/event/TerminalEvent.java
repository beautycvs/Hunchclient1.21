package dev.hunchclient.event;

import dev.hunchclient.module.impl.terminalsolver.TerminalHandler;

/** Terminal-related events. */
public abstract class TerminalEvent extends CancellableEvent {
    public final TerminalHandler terminal;

    public TerminalEvent(TerminalHandler terminal) {
        this.terminal = terminal;
    }

    public static class Opened extends TerminalEvent {
        public Opened(TerminalHandler terminal) {
            super(terminal);
        }
    }

    public static class Updated extends TerminalEvent {
        public Updated(TerminalHandler terminal) {
            super(terminal);
        }
    }

    public static class Closed extends TerminalEvent {
        public Closed(TerminalHandler terminal) {
            super(terminal);
        }
    }

    public static class Solved extends TerminalEvent {
        public Solved(TerminalHandler terminal) {
            super(terminal);
        }
    }
}
