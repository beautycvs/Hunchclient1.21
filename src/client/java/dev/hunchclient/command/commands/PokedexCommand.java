package dev.hunchclient.command.commands;

import dev.hunchclient.command.Command;
import dev.hunchclient.gui.PokedexScreen;
import dev.hunchclient.module.impl.PokedexManager;
import dev.hunchclient.module.impl.PokedexManager.CapturedEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Pokedex command - Manual capture and management
 *
 * Usage:
 * .pokedex - Open Pokedex GUI
 * .pokedex capture <uid> <name> - Manually capture a user
 * .pokedex list - List captured users
 * .pokedex stats - Show capture statistics
 * .pokedex reload - Reload user list from server
 */
public class PokedexCommand extends Command {

    private final Minecraft mc = Minecraft.getInstance();

    @Override
    public String getName() {
        return "pokedex";
    }

    @Override
    public String getDescription() {
        return "Capture and collect OG users!";
    }

    @Override
    public String getUsage() {
        return "pokedex [capture|list|stats|reload|reset|gui] [uid] [name]";
    }

    @Override
    public String[] getAliases() {
        return new String[]{"pdex", "pokemon"};
    }

    @Override
    public Category getCategory() {
        return Category.SOCIAL;
    }

    @Override
    public void execute(String[] args) {
        PokedexManager pokedex = PokedexManager.getInstance();

        if (args.length == 0) {
            // Open GUI
            openGui();
            return;
        }

        String action = args[0].toLowerCase();

        switch (action) {
            case "capture":
            case "catch":
            case "add":
                handleCapture(args, pokedex);
                break;

            case "list":
            case "captured":
                handleList(pokedex);
                break;

            case "stats":
            case "progress":
                handleStats(pokedex);
                break;

            case "reload":
            case "refresh":
                pokedex.fetchAllUsers();
                sendSuccess("Reloading OG user list from server...");
                break;

            case "reset":
            case "clear":
                handleReset(args, pokedex);
                break;

            case "gui":
            case "open":
                openGui();
                break;

            case "nearby":
            case "scan":
                handleScanNearby(pokedex);
                break;

            default:
                sendError("Unknown action: " + action);
                sendUsage();
        }
    }

    private void openGui() {
        if (mc.player != null) {
            mc.setScreen(new PokedexScreen(null));
        }
    }

    /**
     * Capture a user manually by UID and name
     */
    private void handleCapture(String[] args, PokedexManager pokedex) {
        if (args.length < 3) {
            sendError("Usage: .pokedex capture <uid> <name>");
            sendInfo("Example: .pokedex capture 17 PlayerName");
            return;
        }

        try {
            int uid = Integer.parseInt(args[1]);
            // Join remaining args as name (in case name has spaces)
            String name = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

            if (pokedex.isCaptured(uid)) {
                CapturedEntry entry = pokedex.getCapturedEntry(uid);
                sendWarning("#" + uid + " " + (entry != null ? entry.name : name) + " is already captured!");
            } else {
                // Capture without player entity (manual mode)
                if (pokedex.capture(uid, name)) {
                    sendSuccess("§6★ CAPTURED! §f#" + uid + " " + name);
                    sendInfo("Progress: " + pokedex.getCapturedCount() + "/" + pokedex.getTotalCount());
                } else {
                    sendError("Failed to capture #" + uid);
                }
            }
        } catch (NumberFormatException e) {
            sendError("Invalid UID: " + args[1] + " (must be a number)");
        }
    }

    /**
     * List all captured users
     */
    private void handleList(PokedexManager pokedex) {
        int count = pokedex.getCapturedCount();

        if (count == 0) {
            sendInfo("Your Pokédex is empty! Capture OG users with Shift+RClick (wooden shovel)");
            return;
        }

        sendMessage("§6§l=== OG List (" + count + "/" + pokedex.getTotalCount() + ") ===");

        List<Integer> allUids = pokedex.getAllUids();
        for (int uid : allUids) {
            if (pokedex.isCaptured(uid)) {
                CapturedEntry entry = pokedex.getCapturedEntry(uid);
                if (entry != null) {
                    sendMessage("§a★ §b#" + uid + " §f" + entry.name + " §7(" + entry.date + ")");
                }
            }
        }
    }

    /**
     * Show capture statistics
     */
    private void handleStats(PokedexManager pokedex) {
        int captured = pokedex.getCapturedCount();
        int total = pokedex.getTotalCount();
        float percentage = total > 0 ? (float) captured / total * 100 : 0;

        sendMessage("§6§l=== OG List Stats ===");
        sendMessage("§aCaptured: §f" + captured + "§7/§f" + total);
        sendMessage("§eProgress: §f" + String.format("%.1f%%", percentage));

        if (!pokedex.isLoaded()) {
            sendWarning("User list not loaded! Use .pokedex reload");
        }

        // Progress bar
        int barWidth = 20;
        int filled = (int) (percentage / 100 * barWidth);
        StringBuilder bar = new StringBuilder("§a");
        for (int i = 0; i < barWidth; i++) {
            bar.append(i < filled ? "█" : "§7░");
        }
        sendMessage(bar.toString());
    }

    /**
     * Reset/clear all captured users
     */
    private void handleReset(String[] args, PokedexManager pokedex) {
        if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
            sendWarning("This will delete ALL captured users from your Pokédex!");
            sendInfo("Type: .pokedex reset confirm");
            return;
        }

        int count = pokedex.getCapturedCount();
        if (count == 0) {
            sendInfo("Your Pokédex is already empty.");
            return;
        }

        pokedex.resetAllCaptured();
        sendSuccess("Pokédex reset! Removed " + count + " captured users.");
    }

    /**
     * Scan nearby players for OG users
     */
    private void handleScanNearby(PokedexManager pokedex) {
        if (mc.level == null || mc.player == null) {
            sendError("Not in a world!");
            return;
        }

        sendMessage("§6§l=== Scanning Nearby Players ===");

        int found = 0;
        int captured = 0;

        for (Player player : mc.level.players()) {
            if (player == mc.player) continue;

            String name = player.getName().getString();
            // Check for NameProtect format: #UID Username
            if (name.matches(".*#\\d+.*")) {
                found++;
                // Extract UID
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("#(\\d+)").matcher(name);
                if (matcher.find()) {
                    int uid = Integer.parseInt(matcher.group(1));
                    boolean isCaptured = pokedex.isCaptured(uid);

                    if (isCaptured) {
                        sendMessage("§a★ §b#" + uid + " §f" + name + " §7(captured)");
                        captured++;
                    } else {
                        sendMessage("§c○ §b#" + uid + " §f" + name + " §e(not captured - Shift+RClick!)");
                    }
                }
            }
        }

        if (found == 0) {
            sendInfo("No OG users nearby");
        } else {
            sendInfo("Found " + found + " OG users (" + captured + " captured)");
        }
    }

    @Override
    public List<String> getSuggestions(String[] args) {
        if (args.length == 0 || (args.length == 1 && args[0].isEmpty())) {
            return Arrays.asList("capture", "list", "stats", "reload", "reset", "gui", "nearby");
        }

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return Arrays.asList("capture", "list", "stats", "reload", "reset", "gui", "nearby")
                .stream()
                .filter(s -> s.startsWith(partial))
                .collect(Collectors.toList());
        }

        // For capture, suggest UIDs that aren't captured yet
        if (args.length == 2 && args[0].equalsIgnoreCase("capture")) {
            PokedexManager pokedex = PokedexManager.getInstance();
            String partial = args[1];
            return pokedex.getAllUids().stream()
                .filter(uid -> !pokedex.isCaptured(uid))
                .map(String::valueOf)
                .filter(s -> s.startsWith(partial))
                .limit(10)
                .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}
