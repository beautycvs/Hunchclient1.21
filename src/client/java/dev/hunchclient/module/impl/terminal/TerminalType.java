package dev.hunchclient.module.impl.terminal;

/** Types of F7 Phase 3 terminals. */
public enum TerminalType {
    MELODY("Click the button on time!", 54),
    RUBIX("Change all to same color!", 45),
    NUMBERS("Click in order!", 36),  // MUST MATCH TerminalTypes.java exactly (lowercase 'o')
    PANES("Correct all the panes!", 45),
    STARTS_WITH("What starts with:", 45),  // Custom title in StartsWithSim: "What starts with: 'X'?"
    SELECT("Select all the", 54);          // Custom title in SelectAllSim: "Select all the X items!"

    private final String displayName;
    private final int windowSize;

    TerminalType(String displayName, int windowSize) {
        this.displayName = displayName;
        this.windowSize = windowSize;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getWindowSize() {
        return windowSize;
    }
}
