package dev.hunchclient.module.impl.sbd;

import dev.hunchclient.module.ConfigurableModule;
import dev.hunchclient.module.Module;
import dev.hunchclient.module.impl.sbd.cache.PartyMember;
import dev.hunchclient.module.impl.sbd.cache.PartyMemberCache;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Party Finder Stats overlay module
 * Shows player stats (cata level, secrets, PB times) in Party Finder tooltips
 *
 * WATCHDOG SAFE: YES
 * - Only modifies client-side tooltips
 * - No server interaction or gameplay changes
 */
public class PartyFinderModule extends Module implements ConfigurableModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(PartyFinderModule.class);
    private static PartyFinderModule instance;

    // Pattern to match player entries in Party Finder (1.21 format)
    // Example: " TerminatorUser: Berserk (45)"
    private static final Pattern PLAYER_ENTRY = Pattern.compile("^\\s+(\\w+):\\s+(\\w+)\\s+\\((\\d+)\\)$");

    // Pattern to detect floor (1.21 format)
    // Example: "Floor: Floor VII"
    private static final Pattern FLOOR_PATTERN = Pattern.compile("Floor:\\s+Floor\\s+([IVXM\\d]+)");

    // Config
    private boolean showClassLevel = true;
    private boolean showCataLevel = true;
    private boolean showSecrets = true;
    private boolean showSecretAverage = true;
    private boolean showPB = true;
    private boolean showMissingClasses = false;

    public PartyFinderModule() {
        super("SBD Party Finder", "Show player stats in Party Finder", Category.MISC, false);
        instance = this;
    }

    public static PartyFinderModule getInstance() {
        return instance;
    }

    @Override
    protected void onEnable() {
        LOGGER.info("SBD Party Finder enabled");
    }

    @Override
    protected void onDisable() {
        LOGGER.info("SBD Party Finder disabled");
    }

    /**
     * Modify tooltip for Party Finder items
     * Called from ItemStackMixin
     */
    public void modifyTooltip(ItemStack stack, List<Component> tooltip) {
        if (!isEnabled() || tooltip.isEmpty()) {
            return;
        }

        // Check if this is a Party Finder item
        String itemName = tooltip.get(0).getString();

        // Check if tooltip contains "Party" (in the title, not necessarily "Party Finder")
        if (!itemName.contains("Party")) {
            return;
        }

        // Extract floor info
        String floor = extractFloor(tooltip);
        if (floor == null) {
            floor = "F7"; // Default
        }

        // Process each line
        for (int i = 0; i < tooltip.size(); i++) {
            String line = tooltip.get(i).getString();
            Matcher matcher = PLAYER_ENTRY.matcher(line);

            if (matcher.find()) {
                String username = matcher.group(1);
                String dungeonClass = matcher.group(2);
                String classLevel = matcher.group(3);

                // Get or create cached player
                PartyMember member = PartyMemberCache.get(username);

                // If not loaded yet, start loading
                if (!member.isDataLoaded() && !member.isLoading()) {
                    member.init();
                }

                // Create enhanced tooltip line
                String enhanced = createEnhancedLine(line, member, floor, username, dungeonClass, classLevel);
                tooltip.set(i, Component.literal(enhanced));
            }
        }

        // Add missing classes if enabled
        if (showMissingClasses && (floor.equals("M4") || floor.equals("M6") || floor.equals("M7"))) {
            List<String> missing = findMissingClasses(tooltip);
            if (!missing.isEmpty()) {
                tooltip.add(Component.literal("§e§lMissing:§r§f " + String.join(", ", missing)));
            }
        }
    }

    private String createEnhancedLine(String originalLine, PartyMember member, String floor, String username, String dungeonClass, String classLevel) {
        // Start with base line (remove any existing suffix markers)
        String base = originalLine.replaceAll("§0§r§r.*", "");

        if (!member.isDataLoaded()) {
            // Still loading
            return base + "§0§r§r §8[§eLoading...§8]";
        }

        if (member.getStats() == null) {
            // API failed completely
            return base + "§0§r§r §8[§cAPI Failed§8]";
        }

        // Even if stats are 0, we still show them (player just hasn't played dungeons)

        StringBuilder suffix = new StringBuilder("§0§r§r");

        // Class Level (if different from original or if showClassLevel is enabled)
        if (showClassLevel) {
            suffix.append(" §b(§e").append(classLevel).append("§b)§r");
        }

        // Cata Level
        if (showCataLevel) {
            if (!showClassLevel) {
                suffix.append(" §b(§e").append(classLevel).append("§b)§r");
            }
            suffix.append(" §b(§6").append(member.getCataLevel()).append("§b)§r");
        }

        // Secrets and Secret Average
        if (showSecrets && showSecretAverage) {
            suffix.append(String.format(" §8[§a%d§8/§b%.1f§8]§r",
                member.getTotalSecrets(),
                member.getSecretAverage()));
        } else if (showSecrets) {
            suffix.append(String.format(" §8[§a%d§8]§r", member.getTotalSecrets()));
        } else if (showSecretAverage) {
            suffix.append(String.format(" §8[§b%.1f§8]§r", member.getSecretAverage()));
        }

        // PB for current floor
        if (showPB) {
            String pbStr = member.getPBString(floor);
            suffix.append(" §8[§9").append(pbStr).append("§8]§r");
        }

        return base + suffix;
    }

    private String extractFloor(List<Component> tooltip) {
        for (Component line : tooltip) {
            String str = line.getString();
            Matcher matcher = FLOOR_PATTERN.matcher(str);
            if (matcher.find()) {
                String floorStr = matcher.group(1);
                return convertRomanToFloor(floorStr);
            }
        }
        return null;
    }

    private String convertRomanToFloor(String roman) {
        return switch (roman) {
            case "I" -> "F1";
            case "II" -> "F2";
            case "III" -> "F3";
            case "IV" -> "F4";
            case "V" -> "F5";
            case "VI" -> "F6";
            case "VII" -> "F7";
            case "M4" -> "M4";
            case "M5" -> "M5";
            case "M6" -> "M6";
            case "M7" -> "M7";
            default -> {
                // Try to parse as number
                try {
                    int num = Integer.parseInt(roman);
                    yield "F" + num;
                } catch (NumberFormatException e) {
                    yield "F7";
                }
            }
        };
    }

    private List<String> findMissingClasses(List<Component> tooltip) {
        List<String> allClasses = List.of("Archer", "Berserk", "Mage", "Tank", "Healer");
        List<String> missing = new java.util.ArrayList<>(allClasses);

        for (Component line : tooltip) {
            String str = line.getString();
            Matcher matcher = PLAYER_ENTRY.matcher(str);
            if (matcher.find()) {
                String dungeonClass = matcher.group(2);
                missing.remove(dungeonClass);
            }
        }

        return missing;
    }

    // Config management
    @Override
    public JsonObject saveConfig() {
        JsonObject config = new JsonObject();
        config.addProperty("showClassLevel", showClassLevel);
        config.addProperty("showCataLevel", showCataLevel);
        config.addProperty("showSecrets", showSecrets);
        config.addProperty("showSecretAverage", showSecretAverage);
        config.addProperty("showPB", showPB);
        config.addProperty("showMissingClasses", showMissingClasses);
        return config;
    }

    @Override
    public void loadConfig(JsonObject config) {
        if (config.has("showClassLevel")) {
            showClassLevel = config.get("showClassLevel").getAsBoolean();
        }
        if (config.has("showCataLevel")) {
            showCataLevel = config.get("showCataLevel").getAsBoolean();
        }
        if (config.has("showSecrets")) {
            showSecrets = config.get("showSecrets").getAsBoolean();
        }
        if (config.has("showSecretAverage")) {
            showSecretAverage = config.get("showSecretAverage").getAsBoolean();
        }
        if (config.has("showPB")) {
            showPB = config.get("showPB").getAsBoolean();
        }
        if (config.has("showMissingClasses")) {
            showMissingClasses = config.get("showMissingClasses").getAsBoolean();
        }
    }

    // Getters and setters
    public boolean isShowClassLevel() {
        return showClassLevel;
    }

    public void setShowClassLevel(boolean show) {
        this.showClassLevel = show;
    }

    public boolean isShowCataLevel() {
        return showCataLevel;
    }

    public void setShowCataLevel(boolean show) {
        this.showCataLevel = show;
    }

    public boolean isShowSecrets() {
        return showSecrets;
    }

    public void setShowSecrets(boolean show) {
        this.showSecrets = show;
    }

    public boolean isShowSecretAverage() {
        return showSecretAverage;
    }

    public void setShowSecretAverage(boolean show) {
        this.showSecretAverage = show;
    }

    public boolean isShowPB() {
        return showPB;
    }

    public void setShowPB(boolean show) {
        this.showPB = show;
    }

    public boolean isShowMissingClasses() {
        return showMissingClasses;
    }

    public void setShowMissingClasses(boolean show) {
        this.showMissingClasses = show;
    }
}
