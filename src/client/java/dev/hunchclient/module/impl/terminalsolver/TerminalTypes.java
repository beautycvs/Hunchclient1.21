package dev.hunchclient.module.impl.terminalsolver;

import dev.hunchclient.module.impl.terminalsolver.gui.*;

/** Enum of all Floor 7 terminal types. */
public enum TerminalTypes {
    PANES("Correct all the panes!", 45) {
        @Override
        public TermGui getGUI() {
            return PanesGui.INSTANCE;
        }
    },
    RUBIX("Change all to same color!", 45) {
        @Override
        public TermGui getGUI() {
            return RubixGui.INSTANCE;
        }
    },
    NUMBERS("Click in order!", 36) {
        @Override
        public TermGui getGUI() {
            return NumbersGui.INSTANCE;
        }
    },
    STARTS_WITH("What starts with:", 45) {
        @Override
        public TermGui getGUI() {
            return StartsWithGui.INSTANCE;
        }
    },
    SELECT("Select all the", 54) {
        @Override
        public TermGui getGUI() {
            return SelectAllGui.INSTANCE;
        }
    },
    MELODY("Click the button on time!", 54) {
        @Override
        public TermGui getGUI() {
            return MelodyGui.INSTANCE;
        }
    };

    public final String windowName;
    public final int windowSize;

    TerminalTypes(String windowName, int windowSize) {
        this.windowName = windowName;
        this.windowSize = windowSize;
    }

    public abstract TermGui getGUI();

    /**
     * Find terminal type by window name
     */
    public static TerminalTypes findByWindowName(String windowName) {
        for (TerminalTypes type : values()) {
            if (windowName.startsWith(type.windowName)) {
                return type;
            }
        }
        return null;
    }
}
