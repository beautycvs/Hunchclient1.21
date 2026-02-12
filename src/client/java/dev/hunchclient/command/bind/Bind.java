package dev.hunchclient.command.bind;

/**
 * Represents a key binding for toggling modules
 */
public class Bind {

    private final int keyCode;
    private final String keyName;
    private final String moduleName;
    private boolean pressed = false;

    public Bind(int keyCode, String keyName, String moduleName) {
        this.keyCode = keyCode;
        this.keyName = keyName;
        this.moduleName = moduleName;
    }

    public int getKeyCode() {
        return keyCode;
    }

    public String getKeyName() {
        return keyName;
    }

    public String getModuleName() {
        return moduleName;
    }

    public boolean isPressed() {
        return pressed;
    }

    public void setPressed(boolean pressed) {
        this.pressed = pressed;
    }

    public boolean isMouse() {
        return keyCode < 0; // Negative values indicate mouse buttons
    }

    public int getMouseButton() {
        return isMouse() ? -keyCode : -1;
    }

    @Override
    public String toString() {
        return keyName + " -> " + moduleName;
    }
}