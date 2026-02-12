package dev.hunchclient.module.impl.dungeons;

import com.google.gson.JsonObject;
import dev.hunchclient.module.ConfigurableModule;
import dev.hunchclient.module.Module;
import dev.hunchclient.module.SettingsProvider;
import dev.hunchclient.module.setting.CheckboxSetting;
import dev.hunchclient.module.setting.ModuleSetting;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;


public class DungeonOptimizerModule extends Module implements ConfigurableModule, SettingsProvider {

    private static DungeonOptimizerModule instance;

    // CancelInteract settings (migrated from CancelInteractModule)
    private boolean cancelInteractEnabled = true;
    private boolean onlyWithAbility = false;
    private boolean noBreakReset = false;

    private boolean preventPlacingWeapons = true;
    private boolean preventPlacingHeads = true;
    private boolean removeDamageTag = false;

    private static final Set<String> WEAPON_IDS = Set.of(
        "FLOWER_OF_TRUTH",
        "BOUQUET_OF_LIES",
        "MOODY_GRAPPLESHOT",
        "BAT_WAND",
        "STARRED_BAT_WAND",
        "WEIRD_TUBA",
        "WEIRDER_TUBA",
        "PUMPKIN_LAUNCHER",
        "FIRE_FREEZE_STAFF"
    );

    // Blocks that should allow interaction (whitelist)
    private static final Set<Block> INTERACTION_WHITELIST = Set.of(
        Blocks.LEVER,
        Blocks.CHEST,
        Blocks.TRAPPED_CHEST,
        Blocks.STONE_BUTTON,
        Blocks.OAK_BUTTON
    );

    // Blocks that should cancel interaction (blacklist)
    private static final Set<Block> INTERACTION_BLACKLIST = Set.of(
        Blocks.COBBLESTONE_WALL,
        Blocks.OAK_FENCE, Blocks.DARK_OAK_FENCE, Blocks.ACACIA_FENCE,
        Blocks.BIRCH_FENCE, Blocks.JUNGLE_FENCE, Blocks.CRIMSON_FENCE,
        Blocks.MANGROVE_FENCE, Blocks.SPRUCE_FENCE, Blocks.WARPED_FENCE,
        Blocks.BAMBOO_FENCE, Blocks.NETHER_BRICK_FENCE, Blocks.PALE_OAK_FENCE,
        Blocks.BIRCH_FENCE_GATE, Blocks.ACACIA_FENCE_GATE, Blocks.DARK_OAK_FENCE_GATE,
        Blocks.OAK_FENCE_GATE, Blocks.JUNGLE_FENCE_GATE, Blocks.CRIMSON_FENCE_GATE,
        Blocks.MANGROVE_FENCE_GATE, Blocks.SPRUCE_FENCE_GATE, Blocks.WARPED_FENCE_GATE,
        Blocks.BAMBOO_FENCE_GATE, Blocks.PALE_OAK_FENCE_GATE,
        Blocks.HOPPER
    );

    private static final String[] ABILITY_KEYWORDS = {
        "ability:", "right click", "left click", "shift click", "sneak right"
    };

    private static final String[] MINING_KEYWORDS = {
        "gauntlet", "drill", "pickaxe", "mining speed", "breaking power"
    };

    // Damage tag regex pattern
    private static final Pattern DAMAGE_TAG_REGEX = Pattern.compile("^.?\\d[\\d,.]+.*?$");

    public DungeonOptimizerModule() {
        super("DungeonOptimizer", "Dungeon optimizations: cancel interact, prevent placing, remove damage tags.", Category.DUNGEONS, RiskLevel.SAFE);
        instance = this;
    }

    public static DungeonOptimizerModule getInstance() {
        return instance;
    }

    @Override
    protected void onEnable() {
        // No-op
    }

    @Override
    protected void onDisable() {
        // No-op
    }

    // ========== CancelInteract Logic (migrated) ==========

    /**
     * Determines whether the current block interaction should be cancelled.
     */
    public boolean shouldCancelBlockInteraction(Level world, BlockPos blockPos) {
        if (!isEnabled() || !cancelInteractEnabled || world == null || blockPos == null) {
            return false;
        }

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) {
            return false;
        }

        ItemStack heldStack = client.player.getMainHandItem();
        if (heldStack.isEmpty()) {
            heldStack = client.player.getItemInHand(InteractionHand.OFF_HAND);
        }

        if (heldStack.isEmpty()) {
            return false;
        }

        BlockState state = world.getBlockState(blockPos);
        Block block = state.getBlock();

        if (INTERACTION_WHITELIST.contains(block)) {
            return false;
        }

        if (heldStack.is(Items.ENDER_PEARL)) {
            return true;
        }

        if (onlyWithAbility && !hasAbilityTooltip(heldStack)) {
            return false;
        }

        return INTERACTION_BLACKLIST.contains(block) || state.isAir();
    }

    /**
     * Determines whether block breaking progress should continue even if the stack's components changed.
     */
    public boolean shouldContinueBreaking(BlockPos queriedPos, boolean vanillaResult, BlockPos currentBreakingPos, ItemStack currentStack, ItemStack selectedStack) {
        if (!isEnabled()) {
            return vanillaResult;
        }

        if (vanillaResult) {
            return true;
        }

        if (!noBreakReset || currentBreakingPos == null || !currentBreakingPos.equals(queriedPos)) {
            return vanillaResult;
        }

        if (currentStack == null || selectedStack == null || currentStack.isEmpty() || selectedStack.isEmpty()) {
            return vanillaResult;
        }

        if (!currentStack.is(selectedStack.getItem())) {
            return vanillaResult;
        }

        return hasMiningLore(currentStack);
    }

    // ========== Prevent Placing Weapons ==========

    /**
     * Check if placing this item should be prevented (weapon check).
     */
    public boolean shouldPreventPlacingWeapon(ItemStack stack) {
        if (!isEnabled() || !preventPlacingWeapons || stack == null || stack.isEmpty()) {
            return false;
        }

        String skyblockId = getSkyblockId(stack);
        return skyblockId != null && WEAPON_IDS.contains(skyblockId);
    }

    // ========== Prevent Placing Heads ==========

    /**
     * Check if placing this player head should be prevented.
     */
    public boolean shouldPreventPlacingHead(ItemStack stack) {
        if (!isEnabled() || !preventPlacingHeads || stack == null || stack.isEmpty()) {
            return false;
        }

        if (!stack.is(Items.PLAYER_HEAD)) {
            return false;
        }

        // Check if it has a skyblock ID (is a skyblock item)
        String skyblockId = getSkyblockId(stack);
        if (skyblockId == null) {
            return false;
        }

        // Check lore for "RIGHT CLICK" or "Right-click"
        List<String> lore = getLore(stack);
        for (String line : lore) {
            if (line.contains("RIGHT CLICK") || line.contains("Right-click")) {
                return true;
            }
        }
        return false;
    }

    // ========== Remove Damage Tag ==========

    /**
     * Check if this armor stand should be removed (damage tag).
     */
    public boolean shouldRemoveDamageTag(Entity entity) {
        if (!isEnabled() || !removeDamageTag) {
            return false;
        }

        if (!(entity instanceof ArmorStand)) {
            return false;
        }

        Component customName = entity.getCustomName();
        if (customName == null) {
            return false;
        }

        String name = ChatFormatting.stripFormatting(customName.getString());
        if (name == null || name.isEmpty()) {
            return false;
        }

        return DAMAGE_TAG_REGEX.matcher(name).matches();
    }

    // ========== Utility Methods ==========

    private boolean hasAbilityTooltip(ItemStack stack) {
        return stack != null && scanTooltip(stack, ABILITY_KEYWORDS);
    }

    private boolean hasMiningLore(ItemStack stack) {
        return stack != null && scanTooltip(stack, MINING_KEYWORDS);
    }

    private boolean scanTooltip(ItemStack stack, String[] keywords) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return false;
        }

        Level world = client.level;
        Options options = client.options;

        Item.TooltipContext context = Item.TooltipContext.of(world);
        TooltipFlag tooltipType = TooltipFlag.Default.NORMAL;
        if (options != null && options.advancedItemTooltips) {
            tooltipType = TooltipFlag.ADVANCED;
        }

        List<Component> tooltip = stack.getTooltipLines(context, client.player, tooltipType);
        for (Component text : tooltip) {
            String stripped = ChatFormatting.stripFormatting(text.getString()).toLowerCase(Locale.ROOT);
            if (stripped.isEmpty()) {
                continue;
            }

            for (String keyword : keywords) {
                if (stripped.contains(keyword)) {
                    return true;
                }
            }
        }

        return false;
    }

    private String getSkyblockId(ItemStack stack) {
        CompoundTag attributes = getExtraAttributes(stack);
        if (attributes == null) {
            return null;
        }
        if (attributes.contains("id")) {
            return attributes.getString("id").orElse(null);
        }
        return null;
    }

    private CompoundTag getExtraAttributes(ItemStack stack) {
        try {
            CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
            if (customData != null) {
                CompoundTag nbt = customData.copyTag();
                if (nbt != null) {
                    if (nbt.contains("ExtraAttributes")) {
                        return nbt.getCompound("ExtraAttributes").orElse(null);
                    }
                    if (nbt.contains("extra_attributes")) {
                        return nbt.getCompound("extra_attributes").orElse(null);
                    }
                    if (nbt.contains("id")) {
                        return nbt;
                    }
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    private List<String> getLore(ItemStack stack) {
        List<String> result = new ArrayList<>();
        Minecraft client = Minecraft.getInstance();
        if (client == null || stack == null || stack.isEmpty()) {
            return result;
        }

        Level world = client.level;
        Item.TooltipContext context = Item.TooltipContext.of(world);
        List<Component> tooltip = stack.getTooltipLines(context, client.player, TooltipFlag.Default.NORMAL);
        for (Component text : tooltip) {
            result.add(ChatFormatting.stripFormatting(text.getString()));
        }
        return result;
    }

    // ========== Getters/Setters ==========

    public boolean isCancelInteractEnabled() { return cancelInteractEnabled; }
    public void setCancelInteractEnabled(boolean v) { this.cancelInteractEnabled = v; }

    public boolean isOnlyWithAbility() { return onlyWithAbility; }
    public void setOnlyWithAbility(boolean v) { this.onlyWithAbility = v; }

    public boolean isNoBreakReset() { return noBreakReset; }
    public void setNoBreakReset(boolean v) { this.noBreakReset = v; }

    public boolean isPreventPlacingWeapons() { return preventPlacingWeapons; }
    public void setPreventPlacingWeapons(boolean v) { this.preventPlacingWeapons = v; }

    public boolean isPreventPlacingHeads() { return preventPlacingHeads; }
    public void setPreventPlacingHeads(boolean v) { this.preventPlacingHeads = v; }

    public boolean isRemoveDamageTag() { return removeDamageTag; }
    public void setRemoveDamageTag(boolean v) { this.removeDamageTag = v; }

    // ========== Config Persistence ==========

    @Override
    public JsonObject saveConfig() {
        JsonObject obj = new JsonObject();
        obj.addProperty("cancelInteractEnabled", cancelInteractEnabled);
        obj.addProperty("onlyWithAbility", onlyWithAbility);
        obj.addProperty("noBreakReset", noBreakReset);
        obj.addProperty("preventPlacingWeapons", preventPlacingWeapons);
        obj.addProperty("preventPlacingHeads", preventPlacingHeads);
        obj.addProperty("removeDamageTag", removeDamageTag);
        return obj;
    }

    @Override
    public void loadConfig(JsonObject data) {
        if (data == null) return;
        if (data.has("cancelInteractEnabled")) {
            cancelInteractEnabled = data.get("cancelInteractEnabled").getAsBoolean();
        }
        if (data.has("onlyWithAbility")) {
            onlyWithAbility = data.get("onlyWithAbility").getAsBoolean();
        }
        if (data.has("noBreakReset")) {
            noBreakReset = data.get("noBreakReset").getAsBoolean();
        }
        if (data.has("preventPlacingWeapons")) {
            preventPlacingWeapons = data.get("preventPlacingWeapons").getAsBoolean();
        }
        if (data.has("preventPlacingHeads")) {
            preventPlacingHeads = data.get("preventPlacingHeads").getAsBoolean();
        }
        if (data.has("removeDamageTag")) {
            removeDamageTag = data.get("removeDamageTag").getAsBoolean();
        }
    }

    // ========== Settings GUI ==========

    @Override
    public List<ModuleSetting> getSettings() {
        List<ModuleSetting> settings = new ArrayList<>();

        settings.add(new CheckboxSetting(
            "Cancel Interact",
            "Cancel block interactions (fences, hoppers, etc.)",
            "dungopt_cancel_interact",
            this::isCancelInteractEnabled,
            this::setCancelInteractEnabled
        ));

        CheckboxSetting onlyAbilitySetting = new CheckboxSetting(
            "Only With Ability",
            "Only cancel when holding item with ability",
            "dungopt_only_ability",
            this::isOnlyWithAbility,
            this::setOnlyWithAbility
        );
        onlyAbilitySetting.setVisible(this::isCancelInteractEnabled);
        settings.add(onlyAbilitySetting);

        CheckboxSetting noBreakSetting = new CheckboxSetting(
            "No Break Reset",
            "Don't reset mining progress when swapping tools",
            "dungopt_no_break_reset",
            this::isNoBreakReset,
            this::setNoBreakReset
        );
        noBreakSetting.setVisible(this::isCancelInteractEnabled);
        settings.add(noBreakSetting);

        settings.add(new CheckboxSetting(
            "Prevent Placing Weapons",
            "Prevent placing FOT, BOL, and other placeable weapons",
            "dungopt_prevent_weapons",
            this::isPreventPlacingWeapons,
            this::setPreventPlacingWeapons
        ));

        settings.add(new CheckboxSetting(
            "Prevent Placing Heads",
            "Prevent placing player heads with right-click abilities",
            "dungopt_prevent_heads",
            this::isPreventPlacingHeads,
            this::setPreventPlacingHeads
        ));

        settings.add(new CheckboxSetting(
            "Remove Damage Tags",
            "Remove armor stand damage number tags in dungeons",
            "dungopt_remove_damage_tag",
            this::isRemoveDamageTag,
            this::setRemoveDamageTag
        ));

        return settings;
    }
}
