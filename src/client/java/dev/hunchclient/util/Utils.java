package dev.hunchclient.util;

/** Miscellaneous utility helpers. */
public class Utils {

    /**
     * Checks if the object equals at least one of the provided options
     */
    public static boolean equalsOneOf(Object obj, Object... options) {
        if (obj == null) {
            for (Object option : options) {
                if (option == null) return true;
            }
            return false;
        }

        for (Object option : options) {
            if (obj.equals(option)) return true;
        }
        return false;
    }

    /**
     * Checks if an int equals at least one of the provided options
     */
    public static boolean equalsOneOf(int value, int... options) {
        for (int option : options) {
            if (value == option) return true;
        }
        return false;
    }
}
