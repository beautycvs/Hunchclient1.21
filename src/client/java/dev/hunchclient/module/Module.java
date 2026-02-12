/*
 * Decompiled with CFR 0.152.
 */
package dev.hunchclient.module;

import dev.hunchclient.HunchClient;
import java.util.Random;

public abstract class Module {
    protected final String name;
    protected final String description;
    protected final Category category;
    protected boolean enabled;
    protected boolean toggleable = true;
    protected final RiskLevel riskLevel;
    protected final Random random = new Random();

    /**
     * Risk level for modules - determines warning behavior
     */
    public enum RiskLevel {
        SAFE,        // No warning, normal toggle
        RISKY,       // Warning sign shown, but normal toggle
        VERY_RISKY   // Warning sign + confirmation screen required
    }

    public Module(String name, String description, Category category, RiskLevel riskLevel) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.riskLevel = riskLevel;
        this.enabled = false;
    }

    /**
     * Legacy constructor for backwards compatibility
     * All modules using this constructor default to SAFE.
     * Use RiskLevel enum for RISKY or VERY_RISKY modules.
     * @deprecated Use RiskLevel instead of boolean
     */
    @Deprecated
    public Module(String name, String description, Category category, boolean watchdogSafe) {
        this(name, description, category, RiskLevel.SAFE);
    }

    public void toggle() {
        if (!this.toggleable) {
            return;
        }
        this.setEnabled(!this.enabled);
    }

    public void setEnabled(boolean enabled) {
        if (!this.toggleable && !enabled && this.enabled) {
            return;
        }
        if (this.enabled == enabled) {
            return;
        }
        this.enabled = enabled;
        if (this.enabled) {
            if (this.riskLevel != RiskLevel.SAFE) {
                HunchClient.LOGGER.warn("HIGH-RISK module '{}' activated! Use with caution.", (Object)this.name);
            }
            this.onEnable();
            HunchClient.LOGGER.info("Module '{}' enabled", (Object)this.name);
        } else {
            this.onDisable();
            HunchClient.LOGGER.info("Module '{}' disabled", (Object)this.name);
        }
    }

    protected abstract void onEnable();

    protected abstract void onDisable();

    public void onTick() {
        // default no-op
    }

    public String getName() {
        return this.name;
    }

    public String getDescription() {
        return this.description;
    }

    public Category getCategory() {
        return this.category;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    /**
     * @deprecated Use getRiskLevel() instead
     */
    @Deprecated
    public boolean isWatchdogSafe() {
        return this.riskLevel == RiskLevel.SAFE;
    }

    /**
     * Get the risk level of this module
     */
    public RiskLevel getRiskLevel() {
        return this.riskLevel;
    }

    /**
     * Check if this module is risky (shows warning sign)
     * Returns true for RISKY and VERY_RISKY
     */
    public boolean isRisky() {
        return this.riskLevel != RiskLevel.SAFE;
    }

    /**
     * Check if this module requires confirmation before enabling
     * Returns true only for VERY_RISKY
     */
    public boolean requiresConfirmation() {
        return this.riskLevel == RiskLevel.VERY_RISKY;
    }

    public boolean isToggleable() {
        return this.toggleable;
    }

    public void setToggleable(boolean toggleable) {
        this.toggleable = toggleable;
    }

    public static enum Category {
        DUNGEONS("Dungeons", false),
        VISUALS("Visuals", true),
        MOVEMENT("Movement", false),
        MISC("Misc", true),
        AI("AI", true);

        private final String displayName;
        private final boolean defaultSafe;

        private Category(String displayName, boolean defaultSafe) {
            this.displayName = displayName;
            this.defaultSafe = defaultSafe;
        }

        public String getDisplayName() {
            return this.displayName;
        }

        public boolean isDefaultSafe() {
            return this.defaultSafe;
        }
    }
}

