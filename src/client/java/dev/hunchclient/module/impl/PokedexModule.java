package dev.hunchclient.module.impl;

import dev.hunchclient.module.Module;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Pokedex Module - Capture OG users!
 *
 * WATCHDOG SAFE: ✅ YES
 * - Client-side only
 * - No gameplay modifications
 * - Just a fun collection feature
 *
 * Usage:
 * - Hold wooden shovel
 * - Shift + Right-click on a player
 * - If they're an OG user (have UID in name), they get captured!
 */
public class PokedexModule extends Module {

    private static final Minecraft mc = Minecraft.getInstance();

    private static PokedexModule instance;
    private boolean registered = false;

    public PokedexModule() {
        super("Pokedex", "Capture OG users! Shift+RClick with wooden shovel", Category.MISC, true);
        instance = this;
        System.out.println("[Pokedex] Module constructor called!");
    }

    public static PokedexModule getInstance() {
        return instance;
    }

    @Override
    protected void onEnable() {
        if (!registered) {
            UseEntityCallback.EVENT.register(this::onUseEntity);
            registered = true;
        }

        // Initialize PokedexManager
        PokedexManager.getInstance();

        sendClientMessage("§6[Pokedex] §aEnabled! Shift+RClick players with wooden shovel to capture OG users!");
    }

    @Override
    protected void onDisable() {
        // Keep registered, just don't process
    }

    private InteractionResult onUseEntity(Player player, Level world, InteractionHand hand, Entity entity, EntityHitResult hitResult) {
        // Only process on client side (always enabled - no toggle needed for this fun feature)
        if (!world.isClientSide()) {
            return InteractionResult.PASS;
        }

        // Must be our player
        if (player != mc.player) {
            System.out.println("[Pokedex DEBUG] SKIP: not our player");
            return InteractionResult.PASS;
        }

        System.out.println("[Pokedex DEBUG] Checking conditions: shovel=" + isHoldingWoodenShovel() + ", sneaking=" + player.isShiftKeyDown() + ", entity=" + entity.getClass().getSimpleName());

        // Must be holding wooden shovel
        if (!isHoldingWoodenShovel()) {
            System.out.println("[Pokedex DEBUG] SKIP: not holding wooden shovel");
            return InteractionResult.PASS;
        }

        // Must be sneaking (shift)
        if (!player.isShiftKeyDown()) {
            System.out.println("[Pokedex DEBUG] SKIP: not sneaking");
            return InteractionResult.PASS;
        }

        // Must be targeting a player
        if (!(entity instanceof Player targetPlayer)) {
            System.out.println("[Pokedex DEBUG] SKIP: entity is not a player");
            return InteractionResult.PASS;
        }

        // Don't capture yourself
        if (targetPlayer == mc.player) {
            sendClientMessage("§c[Pokedex] You can't capture yourself!");
            return InteractionResult.PASS;
        }

        // Get the real username (not NameProtect formatted)
        String username = targetPlayer.getName().getString();
        System.out.println("[Pokedex DEBUG] Target username: " + username);

        // Look up UID from the loaded uid_mappings
        PokedexManager pokedex = PokedexManager.getInstance();
        System.out.println("[Pokedex DEBUG] PokedexManager loaded=" + pokedex.isLoaded() + ", totalUsers=" + pokedex.getTotalCount());

        int uid = pokedex.getUid(username);
        System.out.println("[Pokedex DEBUG] UID lookup for '" + username + "' = " + uid);

        if (uid == -1) {
            // No UID found - not an OG user
            sendClientMessage("§c[Pokedex] " + username + " is not an OG user!");
            return InteractionResult.PASS;
        }

        boolean newlyCaptured = tryCaptureCompat(pokedex, uid, username, targetPlayer);

        if (newlyCaptured) {
            sendClientMessage("§a[Pokedex] §6★ CAPTURED! §f#" + uid + " " + username);
            sendClientMessage("§7[Pokedex] 📸 Screenshot saved! 🎨 Skin cached!");
            sendClientMessage("§7[Pokedex] Progress: " + pokedex.getCapturedCount() + "/" + pokedex.getTotalCount());

            // Play a sound effect (Pokemon capture sound vibe)
            if (mc.player != null) {
                mc.player.playSound(SoundEvents.PLAYER_LEVELUP, 1.0f, 1.5f);
            }
        } else {
            // Already captured
            sendClientMessage("§e[Pokedex] #" + uid + " " + username + " is already in your Pokedex!");
        }

        return InteractionResult.SUCCESS; // Consume the interaction
    }

    /**
     * Capture with full data (screenshot + skin).
     * Safe because pokedex package is excluded from Skidfuscate obfuscation.
     */
    private boolean tryCaptureCompat(PokedexManager pokedex, int uid, String username, Player targetPlayer) {
        return pokedex.capture(uid, username, targetPlayer, true);
    }

    /**
     * Check if player is holding a wooden shovel in either hand
     */
    private boolean isHoldingWoodenShovel() {
        if (mc.player == null) return false;

        return mc.player.getMainHandItem().is(Items.WOODEN_SHOVEL) ||
               mc.player.getOffhandItem().is(Items.WOODEN_SHOVEL);
    }

    /**
     * Send a message to the player
     */
    private void sendClientMessage(String message) {
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(message), false);
        }
    }
}
