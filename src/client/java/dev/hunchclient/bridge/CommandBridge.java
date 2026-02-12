package dev.hunchclient.bridge;

import dev.hunchclient.command.CommandManager;
import java.util.List;

public final class CommandBridge {

    public static final String SECONDARY_PREFIX = ".";
    public static final String HUNCHCLIENT_PREFIX = "/hunchclient ";
    public static final String HC_PREFIX = "/hc ";

    private CommandBridge() {}

    public static boolean execute(String message) {
        return CommandManager.getInstance().execute(message);
    }

    public static List<String> getSuggestions(String partial) {
        return CommandManager.getInstance().getSuggestions(partial);
    }
}
