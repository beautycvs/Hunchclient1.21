package dev.hunchclient.module.impl.dungeons;

import dev.hunchclient.module.ConfigurableModule;
import dev.hunchclient.module.Module;
import dev.hunchclient.module.SettingsProvider;
import dev.hunchclient.module.setting.*;
import dev.hunchclient.util.Executor;
import com.google.gson.JsonObject;
import java.util.*;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * AlignAura - automatically rotates item frame arrows to the correct positions for the device secret.
 * WATCHDOG SAFE: Uses the same packet technique as the original implementation.
 */
public class AlignAuraModule extends Module implements ConfigurableModule, SettingsProvider {

    // 9 solution patterns for 5x5 grid (25 positions)
    // null = no frame, 0-7 = arrow rotation
    private static final Integer[][] SOLUTIONS = {
        {7,7,7,7,null,1,null,null,null,null,1,3,3,3,3,null,null,null,null,1,null,7,7,7,1},
        {null,null,null,null,null,1,null,1,null,1,1,null,1,null,1,1,null,1,null,1,null,null,null,null,null},
        {5,3,3,3,null,5,null,null,null,null,7,7,null,null,null,1,null,null,null,null,1,3,3,3,null},
        {null,null,null,null,null,null,1,null,1,null,7,1,7,1,3,1,null,1,null,1,null,null,null,null,null},
        {null,null,7,7,5,null,7,1,null,5,null,null,null,null,null,null,7,5,null,1,null,null,7,7,1},
        {7,7,null,null,null,1,null,null,null,null,1,3,3,3,3,null,null,null,null,1,null,null,null,7,1},
        {5,3,3,3,3,5,null,null,null,1,7,7,null,null,1,null,null,null,null,1,null,7,7,7,1},
        {7,7,null,null,null,1,null,null,null,null,1,3,null,7,5,null,null,null,null,5,null,null,null,3,3},
        {null,null,null,null,null,1,3,3,3,3,null,null,null,null,1,7,7,7,7,1,null,null,null,null,null}
    };

    // Device location in dungeon
    private static final BlockPos DEVICE_STAND_LOCATION = new BlockPos(0, 120, 77);
    private static final BlockPos DEVICE_CORNER = new BlockPos(-2, 120, 75);

    private static final long LEGIT_CLICK_DELAY_MS = 45L;

    // State tracking
    private boolean inP3 = false; // Phase 3 (Goldor fight)
    private List<FrameData> currentFrames = null;
    private final long[] recentClicks = new long[25]; // Cooldown tracking for 25 frames
    private boolean legitMode = false;
    private long lastLegitClickTime = 0L;

    private Executor tickExecutor;

    public AlignAuraModule() {
        super("AlignAura", "Auto-aligns arrows in Device secret", Category.DUNGEONS, RiskLevel.VERY_RISKY);

        // Initialize cooldowns
        Arrays.fill(recentClicks, 0);
    }

    @Override
    protected void onEnable() {
        // Start tick loop
        tickExecutor = new Executor(50, this::tickAlignAura); // Run every 50ms (same as CT tick)
        tickExecutor.register();
    }

    @Override
    protected void onDisable() {
        if (tickExecutor != null) {
            tickExecutor.stop();
        }
        currentFrames = null;
        inP3 = false;
        lastLegitClickTime = 0L;
    }

    /**
     * Main tick logic - runs every 50ms
     */
    private void tickAlignAura() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) return;
            if (!isEnabled()) return;

            // Check distance to device (EXACT: Player position, not BlockPos!)
            double dx = mc.player.getX() - DEVICE_STAND_LOCATION.getX();
            double dy = mc.player.getY() - DEVICE_STAND_LOCATION.getY();
            double dz = mc.player.getZ() - DEVICE_STAND_LOCATION.getZ();
            double distSq = dx * dx + dy * dy + dz * dz;

            if (distSq > 100) { // Same as original: distance > 10 blocks
                currentFrames = null;
                return;
            }

        // Get current frame states
        currentFrames = getCurrentFrames();

        // Get rotations array
        List<Integer> rotations = currentFrames.stream()
            .map(frame -> frame != null ? frame.rotation : null)
            .collect(Collectors.toList());

        // Find matching solution
        Integer[] solution = findMatchingSolution(rotations);
        if (solution == null) return;

        // Sort frames by distance (closest first)
        List<Map.Entry<Integer, FrameData>> sortedFrames = new ArrayList<>();
        for (int i = 0; i < currentFrames.size(); i++) {
            if (currentFrames.get(i) != null) {
                sortedFrames.add(new AbstractMap.SimpleEntry<>(i, currentFrames.get(i)));
            }
        }

        sortedFrames.sort((a, b) -> {
            double distA = getDistanceToFrame(mc.player, a.getValue().entity);
            double distB = getDistanceToFrame(mc.player, b.getValue().entity);
            return Double.compare(distA, distB);
        });

        // Click closest frame that needs alignment
        for (Map.Entry<Integer, FrameData> entry : sortedFrames) {
            int index = entry.getKey();
            FrameData frame = entry.getValue();

            // Check if in range (5 blocks)
            double dist = getDistanceToFrame(mc.player, frame.entity);
            if (dist > 25) continue; // 5 blocks squared = 25

            // Calculate clicks needed
            int clicksNeeded = calculateClicksNeeded(solution[index], frame.rotation);
            if (clicksNeeded <= 0) continue;

            // P3 optimization: if only 1 frame left, reduce clicks by 1
            if (!inP3) {
                long wrongFrames = 0;
                for (int i = 0; i < currentFrames.size(); i++) {
                    FrameData f = currentFrames.get(i);
                    if (f != null && calculateClicksNeeded(solution[i], f.rotation) > 0) {
                        wrongFrames++;
                    }
                }

                if (wrongFrames <= 1) {
                    clicksNeeded--;
                }
            }

            if (legitMode) {
                if (clicksNeeded <= 0) {
                    continue;
                }
                if (!isLookingAtFrame(mc, frame.entity)) {
                    continue;
                }
                if (System.currentTimeMillis() - lastLegitClickTime < LEGIT_CLICK_DELAY_MS) {
                    continue;
                }
                clicksNeeded = 1;
            }

            if (clicksNeeded > 0) {
                long now = System.currentTimeMillis();
                recentClicks[index] = now;
                if (legitMode) {
                    lastLegitClickTime = now;
                }
            }

            // Send click packets
            for (int i = 0; i < clicksNeeded; i++) {
                frame.rotation = (frame.rotation + 1) % 8;
                clickFrame(frame.entity);
            }

            break; // Only click one frame per tick
        }
        } catch (Exception e) {
            // Silently catch any errors to prevent crash
        }
    }

    /**
     * Gets current state of all 25 item frames
     */
    private List<FrameData> getCurrentFrames() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return new ArrayList<>();

        // Get all item frames in world
        Map<String, FrameData> frames = new HashMap<>();

        // PERFORMANCE: Use getEntitiesByClass to only get ItemFrameEntities instead of all entities
        List<ItemFrame> itemFrames = mc.level.getEntitiesOfClass(
            ItemFrame.class,
            mc.player.getBoundingBox().inflate(50), // Only search nearby
            frame -> true
        );

        for (ItemFrame frame : itemFrames) {

            BlockPos pos = frame.blockPosition();
            String posStr = pos.getX() + "," + pos.getY() + "," + pos.getZ();

            ItemStack heldItem = frame.getItem();
            if (heldItem.isEmpty() || !heldItem.is(Items.ARROW)) continue;

            int rotation = frame.getRotation();
            frames.put(posStr, new FrameData(frame, rotation));
        }

        // Build 5x5 grid array (25 positions)
        List<FrameData> array = new ArrayList<>();
        int x = DEVICE_CORNER.getX();
        int y0 = DEVICE_CORNER.getY();
        int z0 = DEVICE_CORNER.getZ();

        for (int dz = 0; dz < 5; dz++) {
            for (int dy = 0; dy < 5; dy++) {
                int index = dy + dz * 5;

                // Use cached frame if clicked recently (within 1 second)
                if (currentFrames != null &&
                    index < currentFrames.size() &&
                    currentFrames.get(index) != null &&
                    System.currentTimeMillis() - recentClicks[index] < 1000) {
                    array.add(currentFrames.get(index));
                    continue;
                }

                int y = y0 + dy;
                int z = z0 + dz;
                String posStr = x + "," + y + "," + z;

                if (frames.containsKey(posStr)) {
                    array.add(frames.get(posStr));
                } else {
                    array.add(null);
                }
            }
        }

        return array;
    }

    /**
     * Finds solution that matches current pattern
     */
    private Integer[] findMatchingSolution(List<Integer> rotations) {
        for (Integer[] solution : SOLUTIONS) {
            boolean matches = true;

            for (int i = 0; i < 25; i++) {
                boolean solutionHasFrame = solution[i] != null;
                boolean currentHasFrame = rotations.get(i) != null;

                // XOR: mismatch if one has frame and other doesn't
                if (solutionHasFrame ^ currentHasFrame) {
                    matches = false;
                    break;
                }
            }

            if (matches) return solution;
        }

        return null;
    }

    /**
     * Calculates how many clicks needed to align arrow
     */
    private int calculateClicksNeeded(Integer target, int current) {
        if (target == null) return 0;
        return (target - current + 8) % 8;
    }

    /**
     * Gets squared distance from player eye to frame (EXACT like original!)
     */
    private double getDistanceToFrame(net.minecraft.world.entity.player.Player player, ItemFrame frame) {
        // Original: Player.getY() + Player.getPlayer().func_70047_e()
        // func_70047_e() = getEyeHeight()
        double playerEyeY = player.getY() + player.getEyeHeight();

        double dx = player.getX() - frame.getX();
        double dy = playerEyeY - frame.getY();
        double dz = player.getZ() - frame.getZ();

        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Sends click packets to item frame (EXACTLY like original - 2 packets!)
     */
    private void clickFrame(ItemFrame frame) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;

        // Original sends TWO packets:
        // 1. C02PacketUseEntity with Vec3 offset
        // 2. C02PacketUseEntity with INTERACT action

        // Packet 1: Interact at specific position (Vec3(0.03125, 0, 0))
        ServerboundInteractPacket packet1 = ServerboundInteractPacket.createInteractionPacket(
            frame,
            false, // not sneaking
            InteractionHand.MAIN_HAND,
            new Vec3(0.03125, 0, 0) // Exact same as original
        );
        mc.getConnection().send(packet1);

        // Packet 2: Regular interact
        ServerboundInteractPacket packet2 = ServerboundInteractPacket.createInteractionPacket(
            frame,
            false, // not sneaking
            InteractionHand.MAIN_HAND
        );
        mc.getConnection().send(packet2);
    }

    /**
     * Called when chat message received (for P3 detection)
     */
    public void onChatMessage(String message) {
        if (message.contains("[BOSS] Goldor: Who dares trespass into my domain?")) {
            inP3 = true;
            debugMessage("§aEntered P3 mode");
        } else if (message.contains("The Core entrance is opening!")) {
            inP3 = false;
            debugMessage("§cExited P3 mode");
        }
    }

    /**
     * Called on world unload
     */
    public void onWorldUnload() {
        inP3 = false;
        currentFrames = null;
        lastLegitClickTime = 0L;
        Arrays.fill(recentClicks, 0);
    }

    private void debugMessage(String msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("§7[AlignAura] " + msg), false);
        }
    }

    public boolean isLegitMode() {
        return legitMode;
    }

    public void setLegitMode(boolean legitMode) {
        this.legitMode = legitMode;
        lastLegitClickTime = 0L;
    }

    @Override
    public JsonObject saveConfig() {
        JsonObject data = new JsonObject();
        data.addProperty("legitMode", legitMode);
        return data;
    }

    @Override
    public void loadConfig(JsonObject data) {
        if (data == null) {
            return;
        }
        if (data.has("legitMode")) {
            legitMode = data.get("legitMode").getAsBoolean();
        }
    }

    // SettingsProvider implementation
    @Override
    public List<ModuleSetting> getSettings() {
        List<ModuleSetting> settings = new ArrayList<>();

        settings.add(new CheckboxSetting(
            "Legit Mode",
            "Only click frames you're looking at with delay",
            "alignaura_legit",
            () -> legitMode,
            val -> legitMode = val
        ));

        return settings;
    }

    private boolean isLookingAtFrame(Minecraft mc, ItemFrame frame) {
        if (mc.player == null || frame == null) {
            return false;
        }

        if (!mc.player.hasLineOfSight(frame)) {
            return false;
        }

        if (mc.hitResult instanceof EntityHitResult entityHit && entityHit.getEntity() == frame) {
            return true;
        }

        Vec3 eyePos = mc.player.getEyePosition();
        Vec3 look = mc.player.getViewVector(1.0f).normalize();
        Vec3 toFrame = frame.getBoundingBox().getCenter().subtract(eyePos);

        if (toFrame.lengthSqr() <= 0.0001) {
            return true;
        }

        Vec3 direction = toFrame.normalize();
        double dot = look.dot(direction);
        dot = Math.max(-1.0, Math.min(1.0, dot));
        double angleDeg = Math.toDegrees(Math.acos(dot));
        return angleDeg <= 10.0;
    }

    /**
     * Frame data storage
     */
    private static class FrameData {
        final ItemFrame entity;
        int rotation;

        FrameData(ItemFrame entity, int rotation) {
            this.entity = entity;
            this.rotation = rotation;
        }
    }
}
