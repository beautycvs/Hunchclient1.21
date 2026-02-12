package dev.hunchclient.module.impl.misc;

import dev.hunchclient.module.ConfigurableModule;
import dev.hunchclient.module.Module;
import dev.hunchclient.module.SettingsProvider;
import dev.hunchclient.module.setting.*;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/**
 * Player Trap Module - Mines blocks under a target player
 */
public class PlayerTrapModule extends Module implements ConfigurableModule, SettingsProvider {
    private static final Minecraft MC = Minecraft.getInstance();

    private double mineRange = 5.0; // Max distance to target player (WICHTIG: max 5.0 für ban safety!)
    private int blocksUnderPlayer = 3; // How many blocks below to mine
    private boolean debugMessages = false; // Default: false (less spam)
    private int blockRespawnDelayMs = 1000; // 1 second cooldown before re-mining same block (RDBT-style)

    private Player targetPlayer = null;
    private int currentTick = 0;
    private final Map<BlockPos, Long> minedBlocksWithTime = new HashMap<>(); // Track recently mined blocks

    // Keybind
    private static net.minecraft.client.KeyMapping trapKey;

    public PlayerTrapModule() {
        super("PlayerTrap", "Mines blocks under the closest player (hold keybind)", Category.MISC, RiskLevel.RISKY);

        // Register keybind
        if (trapKey == null) {
            trapKey = net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(
                new net.minecraft.client.KeyMapping(
                    "key.hunchclient.playertrap",
                    com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM,
                    org.lwjgl.glfw.GLFW.GLFW_KEY_G,
                    dev.hunchclient.HunchModClient.KEYBIND_CATEGORY
                )
            );
        }

        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    @Override
    protected void onEnable() {
        if (MC.player != null) {
            MC.player.displayClientMessage(Component.literal("§a[PlayerTrap] Enabled! Auto-targeting closest player in " + String.format("%.1f", mineRange) + "m range"), false);
        }
    }

    @Override
    protected void onDisable() {
        targetPlayer = null;
        minedBlocksWithTime.clear();
    }

    private void onTick(Minecraft client) {
        if (!isEnabled() || MC.player == null || MC.level == null) return;

        // WICHTIG: Only mine when keybind is pressed
        if (!trapKey.isDown()) {
            return;
        }

        currentTick++;

        // Find closest player (auto-target)
        targetPlayer = null;
        double closestDistance = mineRange;

        for (Player player : MC.level.players()) {
            // Skip self
            if (player == MC.player) continue;

            // Calculate distance
            double distance = new Vec3(MC.player.getX(), MC.player.getY(), MC.player.getZ()).distanceTo(new Vec3(player.getX(), player.getY(), player.getZ()));

            // Check if in range and closer than current target
            if (distance <= mineRange && distance < closestDistance) {
                targetPlayer = player;
                closestDistance = distance;
            }
        }

        if (targetPlayer == null) {
            if (debugMessages && currentTick % 100 == 0) {
                MC.player.displayClientMessage(Component.literal("§e[PlayerTrap] No players in range (" + String.format("%.1f", mineRange) + "m)"), false);
            }
            return;
        }

        // Mine blocks under closest player
        mineBlocksUnderPlayer();
    }

    private void mineBlocksUnderPlayer() {
        if (targetPlayer == null || MC.player == null || MC.level == null) return;

        // WICHTIG: Check distance to TARGET PLAYER, not to blocks!
        double distanceToTarget = new Vec3(MC.player.getX(), MC.player.getY(), MC.player.getZ()).distanceTo(new Vec3(targetPlayer.getX(), targetPlayer.getY(), targetPlayer.getZ()));
        if (distanceToTarget > mineRange) {
            if (debugMessages && currentTick % 100 == 0) {
                MC.player.displayClientMessage(Component.literal("§e[PlayerTrap] Target out of range: " + String.format("%.1f", distanceToTarget) + "m"), false);
            }
            return;
        }

        // Clean up old entries from minedBlocksWithTime (older than blockRespawnDelayMs)
        long currentTime = System.currentTimeMillis();
        minedBlocksWithTime.entrySet().removeIf(entry ->
            currentTime - entry.getValue() >= blockRespawnDelayMs
        );

        BlockPos playerPos = targetPlayer.blockPosition();
        Vec3 myEyePos = MC.player.getEyePosition();

        // RDBT-STYLE INSTAMINING:
        // Find CLOSEST unmined block and nuke it (1 block per tick)
        // Track mined blocks to avoid re-mining same block immediately

        BlockPos closestBlock = null;
        double closestDistance = Double.MAX_VALUE;

        // Go through Y levels first (top to bottom), then 3x3 grid
        for (int y = 1; y <= blocksUnderPlayer; y++) {
            // 3x3 grid centered on player
            for (int xOffset = -1; xOffset <= 1; xOffset++) {
                for (int zOffset = -1; zOffset <= 1; zOffset++) {
                    BlockPos blockBelow = playerPos.offset(xOffset, -y, zOffset);

                    // Skip if recently mined
                    if (minedBlocksWithTime.containsKey(blockBelow)) continue;

                    // Check if block exists (not air)
                    if (MC.level.getBlockState(blockBelow).isAir()) continue;

                    // Calculate distance from this block to my eye position
                    double distance = myEyePos.distanceTo(new Vec3(blockBelow.getX() + 0.5, blockBelow.getY() + 0.5, blockBelow.getZ() + 0.5));

                    // Track closest block
                    if (distance < closestDistance) {
                        closestDistance = distance;
                        closestBlock = blockBelow;
                    }
                }
            }
        }

        // Mine the closest block only (1 per tick) - RDBT style
        if (closestBlock != null) {
            Direction facing = getClosestFacing(closestBlock);
            MC.player.connection.send(
                new net.minecraft.network.protocol.game.ServerboundPlayerActionPacket(
                    net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                    closestBlock,
                    facing
                )
            );
            MC.player.swing(InteractionHand.MAIN_HAND, true);

            // Mark as mined with timestamp
            minedBlocksWithTime.put(closestBlock, currentTime);

            if (debugMessages) {
                MC.player.displayClientMessage(Component.literal("§a[PlayerTrap] Nuked: " + closestBlock.toShortString() + " (" + String.format("%.1f", closestDistance) + "m)"), false);
            }
        } else if (debugMessages && currentTick % 100 == 0) {
            MC.player.displayClientMessage(Component.literal("§e[PlayerTrap] No blocks to mine under " + targetPlayer.getName().getString()), false);
        }
    }

    /**
     * Find the closest Direction (block face) to the player's eye position
     */
    private Direction getClosestFacing(BlockPos blockPos) {
        if (MC.player == null) return Direction.UP;

        Vec3 playerEyePos = MC.player.getEyePosition();
        double minDistance = Double.MAX_VALUE;
        Direction closestFace = Direction.UP;

        // Check all 6 faces
        for (Direction face : Direction.values()) {
            // Get offset for this face
            Vec3 offset = Vec3.atLowerCornerOf(face.getUnitVec3i());

            // Calculate center point of this face
            Vec3 faceCenter = new Vec3(
                blockPos.getX() + 0.5 + offset.x * 0.5,
                blockPos.getY() + 0.5 + offset.y * 0.5,
                blockPos.getZ() + 0.5 + offset.z * 0.5
            );

            // Calculate distance from player eyes to this face
            double distance = playerEyePos.distanceTo(faceCenter);

            if (distance < minDistance) {
                minDistance = distance;
                closestFace = face;
            }
        }

        return closestFace;
    }

    @Override
    public List<ModuleSetting> getSettings() {
        List<ModuleSetting> settings = new ArrayList<>();

        settings.add(new SliderSetting(
            "Mine Range",
            "Max distance to target player (MAX 5.0 for ban safety!)",
            "mine_range",
            1.0f, 5.0f,
            () -> (float) mineRange,
            val -> mineRange = Math.min(val, 5.0) // Force max 5.0
        ).withDecimals(1).withSuffix("m"));

        settings.add(new SliderSetting(
            "Blocks Under Player",
            "How many blocks below player to mine",
            "blocks_under",
            1f, 10f,
            () -> (float) blocksUnderPlayer,
            val -> blocksUnderPlayer = (int) val.floatValue()
        ).withSuffix(" blocks"));

        settings.add(new CheckboxSetting(
            "Debug Messages",
            "Show debug messages in chat",
            "debug_messages",
            () -> debugMessages,
            val -> debugMessages = val
        ));

        return settings;
    }

    @Override
    public JsonObject saveConfig() {
        JsonObject config = new JsonObject();
        config.addProperty("mineRange", mineRange);
        config.addProperty("blocksUnderPlayer", blocksUnderPlayer);
        config.addProperty("debugMessages", debugMessages);
        return config;
    }

    @Override
    public void loadConfig(JsonObject config) {
        try {
            if (config.has("mineRange")) {
                mineRange = config.get("mineRange").getAsDouble();
            }
            if (config.has("blocksUnderPlayer")) {
                blocksUnderPlayer = config.get("blocksUnderPlayer").getAsInt();
            }
            if (config.has("debugMessages")) {
                debugMessages = config.get("debugMessages").getAsBoolean();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
