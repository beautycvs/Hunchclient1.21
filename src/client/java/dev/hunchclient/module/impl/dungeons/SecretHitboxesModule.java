package dev.hunchclient.module.impl.dungeons;

import dev.hunchclient.module.ConfigurableModule;
import dev.hunchclient.module.Module;
import dev.hunchclient.module.SettingsProvider;
import dev.hunchclient.module.setting.CheckboxSetting;
import dev.hunchclient.module.setting.ModuleSetting;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.SkullBlockEntity;

/**
 * Secret Hitboxes Module
 *
 * Extends the hitboxes of secret blocks to a full block in dungeons
 *
 * WATCHDOG SAFE: YES
 * - Client-side only rendering/hitbox changes
 * - No packets sent
 * - No server-side effects
 */
public class SecretHitboxesModule extends Module implements ConfigurableModule, SettingsProvider, dev.hunchclient.bridge.module.ISecretHitboxes {

    private static SecretHitboxesModule INSTANCE;

    // Settings
    private boolean lever = false;
    private boolean button = false;
    private boolean essence = false;
    private boolean chests = false;

    // Wither Essence UUID - most significant bits
    // Hypixel uses a skull with this texture signature for wither essence
    private static final String WITHER_ESSENCE_UUID = "26bb1a8d-7c66-31c6-82d5-a9c04c94fb02";
    private static final long MOST_SIGNIFICANT_BITS = UUID.fromString(WITHER_ESSENCE_UUID).getMostSignificantBits();

    public SecretHitboxesModule() {
        super("SecretHitboxes", "Extends the hitboxes of secret blocks to a full block", Category.DUNGEONS, RiskLevel.RISKY);
        INSTANCE = this;
    }

    public static SecretHitboxesModule getInstance() {
        return INSTANCE;
    }

    @Override
    protected void onEnable() {
        // Nothing special on enable
    }

    @Override
    protected void onDisable() {
        // Nothing special on disable
    }

    @Override
    public void onTick() {
        // Not needed for this module - mixins handle everything
    }

    // ==================== Hitbox Checks (called from mixins) ====================

    public boolean isLeverEnabled() {
        return isEnabled() && lever;
    }

    public boolean isButtonEnabled() {
        return isEnabled() && button;
    }

    public boolean isChestsEnabled() {
        return isEnabled() && chests;
    }

    public boolean isEssenceEnabled() {
        return isEnabled() && essence;
    }

    /**
     * Check if a block position contains a wither essence skull
     * Called from SkullBlockMixin
     */
    public boolean isEssence(BlockPos pos) {
        if (!isEssenceEnabled()) {
            return false;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return false;
        }

        if (mc.level.getBlockEntity(pos) instanceof SkullBlockEntity skull) {
            var profileComponent = skull.getOwnerProfile();
            if (profileComponent != null) {
                // TODO 1.21.10: ProfileComponent API changed - profile() doesn't exist, need to find correct method
                // Temporarily disabled - check Yarn mappings for correct accessor
                /*var gameProfile = profileComponent.profile();
                if (gameProfile != null && gameProfile.getId() != null) {
                    return gameProfile.getId().getMostSignificantBits() == MOST_SIGNIFICANT_BITS;
                }*/
            }
        }

        return false;
    }

    // ==================== SettingsProvider Implementation ====================

    @Override
    public List<ModuleSetting> getSettings() {
        List<ModuleSetting> settings = new ArrayList<>();

        settings.add(new CheckboxSetting("Lever", "Extends the lever hitbox", "secret_hitbox_lever",
            () -> lever,
            (value) -> { lever = value; saveConfig(); }));

        settings.add(new CheckboxSetting("Button", "Extends the button hitbox", "secret_hitbox_button",
            () -> button,
            (value) -> { button = value; saveConfig(); }));

        settings.add(new CheckboxSetting("Essence", "Extends the wither essence hitbox", "secret_hitbox_essence",
            () -> essence,
            (value) -> { essence = value; saveConfig(); }));

        settings.add(new CheckboxSetting("Chests", "Extends the chest hitbox", "secret_hitbox_chests",
            () -> chests,
            (value) -> { chests = value; saveConfig(); }));

        return settings;
    }

    // ==================== ConfigurableModule Implementation ====================

    @Override
    public JsonObject saveConfig() {
        JsonObject json = new JsonObject();
        json.addProperty("enabled", isEnabled());
        json.addProperty("lever", lever);
        json.addProperty("button", button);
        json.addProperty("essence", essence);
        json.addProperty("chests", chests);
        return json;
    }

    @Override
    public void loadConfig(JsonObject json) {
        if (json.has("enabled")) setEnabled(json.get("enabled").getAsBoolean());
        if (json.has("lever")) lever = json.get("lever").getAsBoolean();
        if (json.has("button")) button = json.get("button").getAsBoolean();
        if (json.has("essence")) essence = json.get("essence").getAsBoolean();
        if (json.has("chests")) chests = json.get("chests").getAsBoolean();
    }
}
