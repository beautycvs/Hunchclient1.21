package dev.hunchclient.module.impl;

import dev.hunchclient.module.ConfigurableModule;
import dev.hunchclient.module.Module;
import dev.hunchclient.module.SettingsProvider;
import dev.hunchclient.module.setting.*;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;

/**
 * AutoLeap Module - Announces Spirit Leap teleports to party chat
 *
 * WATCHDOG SAFE: YES
 * - Client-side chat message handling only
 * - No packets sent beyond normal chat
 * - No automation of gameplay
 */
public class AutoLeapModule extends Module implements ConfigurableModule, SettingsProvider, dev.hunchclient.bridge.module.IAutoLeap {

    private boolean announceLeaps = true;
    private String customLeapMessage = "Leaping to {name}!";
    private List<ModuleSetting> settings;

    public AutoLeapModule() {
        super("AutoLeap", "Announces Spirit Leap teleports to party chat", Category.MISC, false);
        initializeSettings();
    }

    private void initializeSettings() {
        settings = new ArrayList<>();

        settings.add(new CheckboxSetting(
            "Announce Leaps",
            "Announce Spirit Leap teleports in party chat",
            "announceLeaps",
            () -> announceLeaps,
            (value) -> announceLeaps = value
        ));

        settings.add(new TextBoxSetting(
            "Leap Message",
            "Custom message format. Use {name} for player name",
            "customLeapMessage",
            () -> customLeapMessage,
            (value) -> setCustomLeapMessage(value),
            "Leaping to {name}!"
        ));
    }

    @Override
    public List<ModuleSetting> getSettings() {
        return settings;
    }

    @Override
    protected void onEnable() {
        // No initialization needed
    }

    @Override
    protected void onDisable() {
        // No cleanup needed
    }

    /**
     * Check if message matches leap teleport and announce to party
     * Called from ChatHudMixin
     */
    public boolean handleLeapMessage(String message) {
        if (!isEnabled() || !announceLeaps) {
            return false;
        }

        // Match "You have teleported to <player>!"
        if (message.startsWith("You have teleported to ") && message.endsWith("!")) {
            String playerName = message.substring("You have teleported to ".length(), message.length() - 1);

            // Format and send message
            String formattedMessage = customLeapMessage.replace("{name}", playerName);
            Minecraft.getInstance().player.connection.sendChat("/pc " + formattedMessage);

            return true; // Cancel original message
        }

        return false;
    }

    public boolean isAnnounceLeaps() {
        return announceLeaps;
    }

    public void setAnnounceLeaps(boolean announceLeaps) {
        this.announceLeaps = announceLeaps;
    }

    public String getCustomLeapMessage() {
        return customLeapMessage;
    }

    public void setCustomLeapMessage(String customLeapMessage) {
        this.customLeapMessage = customLeapMessage == null || customLeapMessage.isEmpty()
            ? "Leaping to {name}!"
            : customLeapMessage;
    }

    @Override
    public JsonObject saveConfig() {
        JsonObject config = new JsonObject();
        config.addProperty("announceLeaps", announceLeaps);
        config.addProperty("customLeapMessage", customLeapMessage);
        return config;
    }

    @Override
    public void loadConfig(JsonObject data) {
        if (data.has("announceLeaps")) {
            announceLeaps = data.get("announceLeaps").getAsBoolean();
        }
        if (data.has("customLeapMessage")) {
            setCustomLeapMessage(data.get("customLeapMessage").getAsString());
        }
    }
}
