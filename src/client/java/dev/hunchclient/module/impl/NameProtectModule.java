package dev.hunchclient.module.impl;

import dev.hunchclient.module.ConfigurableModule;
import dev.hunchclient.module.Module;
import dev.hunchclient.module.SettingsProvider;
import dev.hunchclient.module.setting.*;
import dev.hunchclient.util.SectionCodeParser;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/**
 * NameProtect Module - Replaces player names globally
 *
 * WATCHDOG SAFE: ✅ YES
 * - Client-side only text replacement
 * - No packets sent
 * - Visual only
 *
 * Behavior:
 * - Remote replacements: applied only when enabled + setting
 * - Local replacement (selfNameReplacement): only applied when enabled + setting
 */
public class NameProtectModule extends Module implements ConfigurableModule, SettingsProvider, dev.hunchclient.bridge.module.INameProtect {

    private static final String REMOTE_URL = "https://34.7.234.242/helper/nameprotect.json";
    private static final String UID_MAPPINGS_URL = "https://34.7.234.242/helper/uid_mappings.json";
    private static final int REFRESH_SECONDS = 300; // 5 minutes

    // Pattern to detect existing UID prefix: #N followed by space (e.g., "#18 ")
    private static final Pattern UID_PATTERN = Pattern.compile("#\\d+ ");

    // SEPARATE maps for clean data - no more merging bugs!
    // Custom names from nameprotect.json (e.g., "blackum" -> "CustomName")
    private final Map<String, String> customNames = new HashMap<>();
    // UIDs from uid_mappings.json (e.g., "blackum" -> 17)
    private final Map<String, Integer> uidMap = new HashMap<>();

    // Combined cache built from customNames + uidMap
    private final Map<String, String> remoteReplacements = new HashMap<>();

    // Cache to track already-sanitized Text objects (prevents duplicate processing)
    // Uses WeakHashMap so Text objects can be garbage collected
    private final Map<Component, Component> sanitizedTextCache = Collections.synchronizedMap(new WeakHashMap<>());
    private static final int ORDERED_TEXT_CACHE_SIZE = 512;
    private final ThreadLocal<Map<FormattedCharSequence, FormattedCharSequence>> sanitizedOrderedTextCache =
        ThreadLocal.withInitial(() -> new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<FormattedCharSequence, FormattedCharSequence> eldest) {
                return size() > ORDERED_TEXT_CACHE_SIZE;
            }
        });

    // Local settings (only applied when enabled)
    private boolean enableCustomName = false;
    private boolean showOtherHunchUsers = true;
    private String selfNameReplacement = "&aYou";

    private final Object replacementLock = new Object();
    private volatile ReplacementRule[] remoteCache = new ReplacementRule[0];
    private long lastFetch = 0;
    private final Gson gson = new Gson();

    public NameProtectModule() {
        super("NameProtect", "Replaces player names globally", Category.MISC, false);

        // Build initial cache
        rebuildCombinedCache();

        // Start background thread to keep server data updated even when module is disabled
        startBackgroundFetcher();

        // Enable by default on fresh configs
        setEnabled(true);
    }

    /**
     * Builds the combined remoteReplacements from customNames + uidMap
     * Format: &3#UID &r + (customName OR originalName)
     * Cache prevents duplicate processing, so original name in replacement is safe
     */
    private void rebuildCombinedCache() {
        synchronized (replacementLock) {
            remoteReplacements.clear();

            // First, add all custom names (without UID)
            for (Map.Entry<String, String> entry : customNames.entrySet()) {
                String lowerName = entry.getKey().toLowerCase(Locale.ROOT);
                remoteReplacements.put(lowerName, entry.getValue());
            }

            // Then, for users with UIDs, prepend UID to their name
            for (Map.Entry<String, Integer> entry : uidMap.entrySet()) {
                String lowerName = entry.getKey().toLowerCase(Locale.ROOT);
                int uid = entry.getValue();
                String uidPrefix = "&3#" + uid + " &r";

                // Check if user has a custom name
                String customName = customNames.get(lowerName);
                String baseName;
                if (customName != null && !customName.isEmpty()) {
                    // Has custom name - use it
                    baseName = customName;
                } else {
                    // No custom name - use original username (capitalize first letter)
                    String originalName = entry.getKey();
                    baseName = originalName.substring(0, 1).toUpperCase() + originalName.substring(1).toLowerCase();
                }

                remoteReplacements.put(lowerName, uidPrefix + baseName);
            }

            rebuildRemoteCacheLocked();
            System.out.println("[NameProtect] Cache rebuilt: " + remoteReplacements.size() + " replacements, " + uidMap.size() + " with UIDs");
        }
    }

    @Override
    protected void onEnable() {
        // fetchRemoteReplacements() automatically chains to fetchUidMappingsFromServerSync()
        fetchRemoteReplacements();
    }

    @Override
    protected void onDisable() {
        // Keep remote replacements in memory for when the module is enabled
    }

    /**
     * Start a background thread that fetches server data continuously
     * This runs REGARDLESS of module enabled state, so server data is always available
     */
    private void startBackgroundFetcher() {
        Thread fetcherThread = new Thread(() -> {
            // Wait a bit for startup to complete before first fetch
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                return;
            }

            while (true) {
                try {
                    long now = System.currentTimeMillis();
                    if (now - lastFetch > REFRESH_SECONDS * 1000L) {
                        // fetchRemoteReplacements() automatically chains to fetchUidMappingsFromServerSync()
                        fetchRemoteReplacements();
                    }
                    Thread.sleep(10000); // Check every 10 seconds
                } catch (InterruptedException e) {
                    break; // Exit if interrupted
                } catch (Exception e) {
                    // Silently continue on error
                }
            }
        }, "NameProtect-Fetcher");
        fetcherThread.setDaemon(true); // Daemon thread exits when main program exits
        fetcherThread.start();
    }

    /**
     * Sanitize a string by applying replacements
     * Remote replacements: only if enabled + showOtherHunchUsers
     * Local replacement: only if enabled + enableCustomName
     */
    public String sanitizeString(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        boolean applyRemote = isEnabled() && showOtherHunchUsers;
        boolean applySelf = isEnabled() && enableCustomName;
        if (!applyRemote && !applySelf) {
            return text;
        }

        // Check if string already contains UID pattern - skip if so
        // Check both with color codes AND without (getString() strips them)
        if (text.contains("\u00A73#") || text.contains("&3#") || UID_PATTERN.matcher(text).find()) {
            return text;
        }

        String result = text;

        // Apply remote replacements if enabled
        if (applyRemote) {
            ReplacementRule[] remoteRules = remoteCache;
            for (ReplacementRule rule : remoteRules) {
                result = replaceIgnoreCase(result, rule);
                if (result.isEmpty()) {
                    break;
                }
            }
        }

        // Apply local replacement only if enabled AND remote didn't already handle it with UID
        if (applySelf && Minecraft.getInstance().player != null) {
            String selfName = Minecraft.getInstance().player.getGameProfile().name();
            if (selfName != null && !selfName.isEmpty()) {
                // Check if remote already has a UID replacement for our name
                // If so, skip self replacement to avoid duplicate UID prefix
                if (applyRemote) {
                    String selfLower = selfName.toLowerCase(Locale.ROOT);
                    synchronized (replacementLock) {
                        String remoteReplacement = remoteReplacements.get(selfLower);
                        if (remoteReplacement != null && remoteReplacement.startsWith("&3#")) {
                            // Remote already handles our name with UID - skip self replacement
                            return result;
                        }
                    }
                }

                // Check if text contains self name (case insensitive)
                int index = indexOfIgnoreCase(result, selfName, selfName.length(), 0);
                if (index >= 0) {
                    // Determine replacement: UID-based or custom
                    String replacement = getEffectiveSelfReplacement();
                    if (replacement != null && !replacement.isEmpty()) {
                        // Convert & codes to § codes for SectionCodeParser
                        String convertedReplacement = replacement.replace('&', '\u00A7');
                        result = replaceIgnoreCase(result, new ReplacementRule(selfName, convertedReplacement));
                    }
                }
            }
        }

        return result;
    }

    public Component sanitizeText(Component text) {
        if (text == null) {
            return text;
        }

        boolean applyRemote = isEnabled() && showOtherHunchUsers;
        boolean applySelf = isEnabled() && enableCustomName;
        if (!applyRemote && !applySelf) {
            return text;
        }

        // Check cache first - if this exact Text object was already processed, return cached result
        Component cached = sanitizedTextCache.get(text);
        if (cached != null) {
            return cached;
        }

        // Check if the full text already contains a UID pattern - if so, skip
        // Note: getString() strips color codes, so we check for "#N " pattern (hash + digit + space)
        String fullString = text.getString();
        if (UID_PATTERN.matcher(fullString).find()) {
            // Already has UID prefix somewhere, don't re-process
            sanitizedTextCache.put(text, text);
            return text;
        }

        // Remote replacements apply only when enabled + showOtherHunchUsers
        // Self replacement only applies when enabled + enableCustomName

        AtomicBoolean changed = new AtomicBoolean(false);
        MutableComponent sanitized = Component.empty();
        sanitized.setStyle(text.getStyle());

        text.visit((style, string) -> {
            String replaced = sanitizeString(string);
            if (!string.equals(replaced)) {
                changed.set(true);
            }
            if (!replaced.isEmpty()) {
                MutableComponent part = SectionCodeParser.parse(replaced, style);
                sanitized.append(part);
            }
            return Optional.empty();
        }, text.getStyle());

        Component result = changed.get() ? sanitized : text;
        sanitizedTextCache.put(text, result);
        return result;
    }

    public FormattedText sanitizeStringVisitable(FormattedText visitable) {
        if (visitable == null) {
            return visitable;
        }

        boolean applyRemote = isEnabled() && showOtherHunchUsers;
        boolean applySelf = isEnabled() && enableCustomName;
        if (!applyRemote && !applySelf) {
            return visitable;
        }

        // Check if already contains UID pattern - skip if so
        StringBuilder sb = new StringBuilder();
        visitable.visit((style, string) -> {
            sb.append(string);
            return Optional.empty();
        }, Style.EMPTY);
        String fullString = sb.toString();
        if (fullString.contains("\u00A73#") || fullString.contains("&3#") || UID_PATTERN.matcher(fullString).find()) {
            return visitable;
        }

        // Remote replacements apply only when enabled + showOtherHunchUsers
        // Self replacement only applies when enabled + enableCustomName

        AtomicBoolean changed = new AtomicBoolean(false);
        MutableComponent sanitized = Component.empty();

        visitable.visit((style, string) -> {
            String replaced = sanitizeString(string);
            if (!string.equals(replaced)) {
                changed.set(true);
            }
            if (!replaced.isEmpty()) {
                MutableComponent part = SectionCodeParser.parse(replaced, style);
                sanitized.append(part);
            }
            return Optional.empty();
        }, Style.EMPTY);

        return changed.get() ? sanitized : visitable;
    }

    public FormattedCharSequence sanitizeOrderedText(FormattedCharSequence orderedText) {
        if (orderedText == null) {
            return orderedText;
        }

        boolean applyRemote = isEnabled() && showOtherHunchUsers;
        boolean applySelf = isEnabled() && enableCustomName;
        if (!applyRemote && !applySelf) {
            return orderedText;
        }

        // Fast-path: no remote rules and no possible self replacement -> nothing to do
        ReplacementRule[] remoteRules = remoteCache;
        if (!applyRemote || remoteRules.length == 0) {
            if (!applySelf || Minecraft.getInstance().player == null) {
                return orderedText;
            }
            String replacement = getEffectiveSelfReplacement();
            if (replacement == null || replacement.isEmpty()) {
                return orderedText;
            }
        }

        // Check cache first - if this exact OrderedText was already processed, return cached result
        Map<FormattedCharSequence, FormattedCharSequence> cache = sanitizedOrderedTextCache.get();
        FormattedCharSequence cached = cache.get(orderedText);
        if (cached != null) {
            return cached;
        }

        // Remote replacements apply only when enabled + showOtherHunchUsers
        // Self replacement only applies when enabled + enableCustomName

        MutableComponent builder = Component.empty();
        orderedText.accept((index, style, codePoint) -> {
            String chars = new String(Character.toChars(codePoint));
            MutableComponent part = Component.literal(chars);
            part.setStyle(style);
            builder.append(part);
            return true;
        });

        Component sanitized = sanitizeText(builder);
        FormattedCharSequence result;
        if (sanitized == builder) {
            result = orderedText;
        } else {
            result = sanitized.getVisualOrderText();
        }
        cache.put(orderedText, result);
        return result;
    }

    /**
     * Add a remote replacement (applied when enabled + showOtherHunchUsers)
     */
    public void addRemoteReplacement(String original, String replacement) {
        if (original == null || original.isEmpty()) return;
        synchronized (replacementLock) {
            remoteReplacements.put(original.toLowerCase(Locale.ROOT), replacement);
            rebuildRemoteCacheLocked();
        }
    }

    /**
     * Remove a remote replacement
     */
    public void removeRemoteReplacement(String original) {
        if (original == null || original.isEmpty()) return;
        synchronized (replacementLock) {
            remoteReplacements.remove(original.toLowerCase(Locale.ROOT));
            rebuildRemoteCacheLocked();
        }
    }

    /**
     * Clear all remote replacements
     */
    public void clearRemoteReplacements() {
        synchronized (replacementLock) {
            remoteReplacements.clear();
            rebuildRemoteCacheLocked();
        }
    }

    /**
     * Get all remote replacements
     */
    public Map<String, String> getRemoteReplacements() {
        return new HashMap<>(remoteReplacements);
    }

    /**
     * Get self name replacement
     */
    public String getSelfNameReplacement() {
        return selfNameReplacement;
    }

    /**
     * Get effective self replacement - uses UID prefix from uid_mappings.json if available
     */
    private String getEffectiveSelfReplacement() {
        String selfName = Minecraft.getInstance().player != null
            ? Minecraft.getInstance().player.getGameProfile().name()
            : null;

        Integer uid = null;
        if (selfName != null && !selfName.isEmpty()) {
            synchronized (replacementLock) {
                uid = uidMap.get(selfName.toLowerCase(Locale.ROOT));
            }
        }

        if (uid != null && uid >= 0) {
            String uidPrefix = "&3#" + uid + " &r";
            if (selfNameReplacement != null && !selfNameReplacement.isEmpty()) {
                return uidPrefix + selfNameReplacement;
            }
            return uidPrefix + (selfName != null ? selfName : "You");
        }

        return selfNameReplacement;
    }

    /**
     * Fetches UID mappings from the public server file (async wrapper)
     * This is the primary source for UID mappings - available to all clients
     */
    public void fetchUidMappingsFromServer() {
        CompletableFuture.runAsync(this::fetchUidMappingsFromServerSync);
    }

    /**
     * Gets UID prefix for a username if they're a known HunchClient user
     * Returns null if no UID mapping exists
     */
    public String getUidPrefixForUser(String username) {
        // Check remote replacements for UID pattern
        String lower = username.toLowerCase(Locale.ROOT);
        synchronized (replacementLock) {
            String replacement = remoteReplacements.get(lower);
            if (replacement != null && replacement.startsWith("&3#")) {
                return replacement;
            }
        }
        return null;
    }

    /**
     * Set self name replacement
     */
    public void setSelfNameReplacement(String replacement) {
        this.selfNameReplacement = replacement;
    }

    private void normalizeRemoteReplacements() {
        synchronized (replacementLock) {
            Map<String, String> copy = new HashMap<>(remoteReplacements);
            remoteReplacements.clear();
            copy.forEach((key, value) -> {
                if (key != null) {
                    remoteReplacements.put(key.toLowerCase(Locale.ROOT), value);
                }
            });
            rebuildRemoteCacheLocked();
        }
    }

    private JsonObject fetchJsonFromUrl(String urlString) throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = URI.create(urlString).toURL();
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(7000);
            connection.setReadTimeout(7000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "HunchClient/1.0");

            if (connection.getResponseCode() != 200) {
                return null;
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Fetch custom names from nameprotect.json.
     * SIMPLE: Just store in customNames map, then rebuild combined cache
     */
    private void fetchRemoteReplacements() {
        CompletableFuture.runAsync(() -> {
            try {
                JsonObject json = fetchJsonFromUrl(REMOTE_URL);
                if (json != null) {
                    JsonObject replacementsJson;
                    if (json.has("replacements")) {
                        replacementsJson = json.getAsJsonObject("replacements");
                    } else if (json.has("map")) {
                        replacementsJson = json.getAsJsonObject("map");
                    } else {
                        replacementsJson = json;
                    }

                    // SIMPLE: Just update customNames map
                    synchronized (replacementLock) {
                        for (String key : replacementsJson.keySet()) {
                            String value = replacementsJson.get(key).getAsString();
                            customNames.put(key.toLowerCase(Locale.ROOT), value);
                        }
                        lastFetch = System.currentTimeMillis();
                    }

                    System.out.println("[NameProtect] Loaded " + replacementsJson.size() + " custom names from server");
                }
            } catch (Exception e) {
                // Silently fail, use local replacements
            }

            // Chain to UID mappings fetch, which will rebuild the combined cache
            fetchUidMappingsFromServerSync();
        });
    }

    /**
     * Fetch UIDs from uid_mappings.json.
     * SIMPLE: Just store in uidMap, then rebuild combined cache
     */
    private void fetchUidMappingsFromServerSync() {
        try {
            JsonObject json = fetchJsonFromUrl(UID_MAPPINGS_URL);
            if (json != null) {
                // SIMPLE: Just update uidMap
                synchronized (replacementLock) {
                    uidMap.clear(); // Clear old data
                    for (String mcUsername : json.keySet()) {
                        int uid = json.get(mcUsername).getAsInt();
                        uidMap.put(mcUsername.toLowerCase(Locale.ROOT), uid);
                    }
                }
                System.out.println("[NameProtect] Loaded " + json.size() + " UIDs from server");
            }
        } catch (Exception e) {
            System.err.println("[NameProtect] Failed to fetch UID mappings: " + e.getMessage());
        }

        // ALWAYS rebuild combined cache at the end
        rebuildCombinedCache();
    }

    private void rebuildRemoteCache() {
        synchronized (replacementLock) {
            rebuildRemoteCacheLocked();
        }
    }

    private void rebuildRemoteCacheLocked() {
        ReplacementRule[] rules = remoteReplacements.entrySet().stream()
            .filter(entry -> entry.getKey() != null && !entry.getKey().isEmpty())
            .map(entry -> {
                // Convert & codes to § codes for proper color rendering
                String replacement = entry.getValue().replace('&', '\u00A7');
                return new ReplacementRule(entry.getKey(), replacement);
            })
            .toArray(ReplacementRule[]::new);
        remoteCache = rules;
    }

    private static String replaceIgnoreCase(String input, ReplacementRule rule) {
        if (input.isEmpty() || rule.keyLength == 0) {
            return input;
        }

        // CRITICAL: If the input already contains the full replacement string,
        // another mixin has already processed this text - skip to avoid duplicate UIDs
        if (indexOfIgnoreCase(input, rule.replacement, rule.replacement.length(), 0) >= 0) {
            return input;
        }

        int index = indexOfIgnoreCase(input, rule.search, rule.keyLength, 0);
        if (index < 0) {
            return input;
        }

        String replacement = rule.replacement;
        StringBuilder sb = new StringBuilder(input.length() + Math.max(0, replacement.length() - rule.keyLength));
        int last = 0;

        do {
            // Check if this occurrence is already preceded by a UID prefix
            // UID prefix pattern: "§3#N §r" where N is a number
            boolean alreadyReplaced = false;
            if (index >= 3) { // Minimum length for "§3#" pattern
                // Look backwards for UID prefix
                int checkStart = Math.max(0, index - 15);
                String before = input.substring(checkStart, index);
                // Check if contains UID pattern (§3# or &3#)
                if (before.contains("\u00A73#") || before.contains("&3#")) {
                    alreadyReplaced = true;
                }
            }

            if (alreadyReplaced) {
                // Skip this occurrence - just copy it as-is
                sb.append(input, last, index + rule.keyLength);
            } else {
                // Apply replacement
                sb.append(input, last, index).append(replacement);
            }
            last = index + rule.keyLength;
            index = indexOfIgnoreCase(input, rule.search, rule.keyLength, last);
        } while (index >= 0);

        sb.append(input, last, input.length());
        return sb.toString();
    }

    private static int indexOfIgnoreCase(String input, String search, int searchLength, int fromIndex) {
        if (searchLength == 0) {
            return -1;
        }
        int max = input.length() - searchLength;
        for (int i = Math.max(0, fromIndex); i <= max; i++) {
            if (input.regionMatches(true, i, search, 0, searchLength)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Strips any UID prefix from a replacement string.
     * Handles both & and § color code variants.
     * Pattern: "&3#N &r" or "§3#N §r" where N is the UID number
     */
    private static String stripUidPrefix(String value) {
        if (value == null) return null;

        // Check for UID prefix pattern: &3#N &r or §3#N §r
        // Can appear multiple times if bug occurred, so strip ALL of them
        String result = value;
        while (true) {
            if (result.startsWith("&3#") || result.startsWith("\u00A73#")) {
                // Find the end of the UID prefix (the &r or §r reset code)
                int resetIndex = result.indexOf("&r");
                if (resetIndex == -1) resetIndex = result.indexOf("\u00A7r");
                if (resetIndex >= 0 && resetIndex + 2 <= result.length()) {
                    result = result.substring(resetIndex + 2);
                } else {
                    break; // Malformed, stop
                }
            } else {
                break; // No more UID prefixes
            }
        }
        return result;
    }

    private static final class ReplacementRule {
        private final String search;
        private final String replacement;
        private final int keyLength;

        private ReplacementRule(String search, String replacement) {
            this.search = search;
            this.replacement = replacement == null ? "" : replacement;
            this.keyLength = this.search.length();
        }
    }

    // ConfigurableModule implementation
    @Override
    public JsonObject saveConfig() {
        JsonObject config = new JsonObject();
        config.addProperty("selfNameReplacement", selfNameReplacement);
        config.addProperty("enableCustomName", enableCustomName);
        config.addProperty("showOtherHunchUsers", showOtherHunchUsers);
        return config;
    }

    @Override
    public void loadConfig(JsonObject config) {
        if (config.has("selfNameReplacement")) {
            selfNameReplacement = config.get("selfNameReplacement").getAsString();
        }
        if (config.has("enableCustomName")) {
            enableCustomName = config.get("enableCustomName").getAsBoolean();
        }
        if (config.has("showOtherHunchUsers")) {
            showOtherHunchUsers = config.get("showOtherHunchUsers").getAsBoolean();
        }
    }

    // SettingsProvider implementation
    @Override
    public List<ModuleSetting> getSettings() {
        List<ModuleSetting> settings = new ArrayList<>();

        settings.add(new CheckboxSetting(
                "Show Hunch Client Users",
                "Apply remote name replacements for other Hunch Client users.",
                "nameprotect_show_other_users",
                () -> showOtherHunchUsers,
                val -> showOtherHunchUsers = val
        ));

        settings.add(new CheckboxSetting(
            "Enable Custom \"Your Name\"",
            "Use your custom name replacement.",
            "nameprotect_enable_custom_name",
            () -> enableCustomName,
            val -> enableCustomName = val
        ));

        // Text input for self name replacement
        settings.add(new TextBoxSetting(
            "Your Name",
            "Replace your name with this text. Supports: &a=green, &c=red, &4=dark_red, &l=bold | MiniMessage: <bold>, <italic>, <gradient:#ff0000:#00ff00>text</gradient>",
            "nameprotect_self_name",
            () -> selfNameReplacement,
            val -> selfNameReplacement = val
        ));

        return settings;
    }
}
