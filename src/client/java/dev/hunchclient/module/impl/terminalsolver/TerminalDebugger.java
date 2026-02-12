package dev.hunchclient.module.impl.terminalsolver;

import dev.hunchclient.HunchClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Terminal Debugger - Stores the last 10 terminal solves.
 * Press keybind to dump compact info to chat.
 */
public class TerminalDebugger {
    private static final TerminalDebugger INSTANCE = new TerminalDebugger();
    private static final int MAX_STORED = 10;

    private final Deque<TerminalEntry> entries = new ConcurrentLinkedDeque<>();

    private TerminalDebugger() {}

    public static TerminalDebugger getInstance() {
        return INSTANCE;
    }

    /**
     * Record a terminal solve result
     */
    public void recordSolve(TerminalHandler handler) {
        recordSolve(handler, "");
    }

    /**
     * Record a terminal solve result with search criteria
     */
    public void recordSolve(TerminalHandler handler, String searchCriteria) {
        // Get item names in order (top-left to bottom-right)
        List<String> itemNames = new ArrayList<>();
        for (int i = 0; i < handler.items.length; i++) {
            ItemStack item = handler.items[i];
            if (item != null && !item.isEmpty()) {
                String name = item.getHoverName().getString();
                // Shorten for readability
                if (name.length() > 20) name = name.substring(0, 17) + "...";
                itemNames.add(i + ":" + name);
            }
        }

        // Copy solution
        List<Integer> solution = new ArrayList<>(handler.solution);

        // Determine status
        String status = solution.isEmpty() ? "ERROR" : "SOLVED";

        // Include search criteria in type string
        String typeWithCriteria = handler.type.name();
        if (searchCriteria != null && !searchCriteria.isEmpty()) {
            typeWithCriteria += "(" + searchCriteria + ")";
        }

        TerminalEntry entry = new TerminalEntry(
            typeWithCriteria,
            status,
            solution,
            itemNames
        );

        entries.addLast(entry);
        while (entries.size() > MAX_STORED) {
            entries.removeFirst();
        }
    }

    /**
     * Dump to chat - compact format
     */
    public void dumpToChat() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        mc.player.displayClientMessage(Component.literal("§6§l[TERM DEBUG] §7Last " + entries.size() + " terminals:"), false);

        if (entries.isEmpty()) {
            mc.player.displayClientMessage(Component.literal("§cNo terminals recorded."), false);
            return;
        }

        int i = 1;
        for (TerminalEntry e : entries) {
            // Format: [1] SELECT_ALL SOLVED [10,11,12,19,20,21]
            String color = e.status.equals("SOLVED") ? "§a" : "§c";
            String solutionStr = e.solution.toString();

            mc.player.displayClientMessage(Component.literal(
                "§7[" + i + "] §f" + e.type + " " + color + e.status + " §f" + solutionStr
            ), false);
            i++;
        }

        // Show last terminal's vanilla items
        if (!entries.isEmpty()) {
            TerminalEntry last = entries.getLast();
            mc.player.displayClientMessage(Component.literal(""), false);
            mc.player.displayClientMessage(Component.literal("§6§lVanilla GUI Items (last terminal):"), false);

            // Show items in rows (9 per row for chest GUI)
            StringBuilder row = new StringBuilder();
            int count = 0;
            for (String itemInfo : last.vanillaItems) {
                if (count > 0 && count % 3 == 0) {
                    mc.player.displayClientMessage(Component.literal("§7" + row.toString()), false);
                    row = new StringBuilder();
                }
                if (row.length() > 0) row.append(" §8| ");
                row.append("§f").append(itemInfo);
                count++;
            }
            if (row.length() > 0) {
                mc.player.displayClientMessage(Component.literal("§7" + row.toString()), false);
            }
        }

        // Also log to console for copy-paste
        HunchClient.LOGGER.info("=== TERM DEBUG DUMP ===");
        for (TerminalEntry e : entries) {
            HunchClient.LOGGER.info("{} {} {}", e.type, e.status, e.solution);
        }
    }

    public void clear() {
        entries.clear();
    }

    /**
     * Simple entry: type, status, solution slots, vanilla items
     */
    public record TerminalEntry(
        String type,
        String status,
        List<Integer> solution,
        List<String> vanillaItems
    ) {}
}
