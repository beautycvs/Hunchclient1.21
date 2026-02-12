package dev.hunchclient.module.impl.dungeons;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CRITICAL PACKET ORDER VALIDATOR
 *
 * Ensures packets are ALWAYS sent in correct order to avoid bans.
 * If packet order is wrong, BLOCK THE ENTIRE SEQUENCE.
 *
 * CORRECT ORDER:
 * 1. UpdateSelectedSlotC2SPacket (slot switch) [OPTIONAL]
 * 2. HandSwingC2SPacket (swing animation)
 * 3. PlayerInteractBlockC2SPacket (block interaction)
 *
 * ANY OTHER ORDER = SEQUENCE BLOCKED
 */
public class PacketOrderValidator {
    private static final Logger LOGGER = LoggerFactory.getLogger("PacketOrderValidator");
    private static final PacketOrderValidator INSTANCE = new PacketOrderValidator();

    // Sequence tracking
    private boolean inPlacementSequence = false;
    private boolean slotSwitchSeen = false;
    private boolean handSwingSeen = false;
    private long sequenceStartTime = 0;
    private static final long SEQUENCE_TIMEOUT_MS = 150; // Sequence must complete within 150ms (3 ticks)

    private boolean enabled = false;

    // Sequence blocking - if packet order is wrong, block ALL remaining packets in sequence
    private boolean blockingSequence = false;
    private long blockSequenceUntil = 0;
    private static final long BLOCK_DURATION_MS = 200; // Block for 200ms (2 ticks)

    private PacketOrderValidator() {}

    public static PacketOrderValidator getInstance() {
        return INSTANCE;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            reset();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * CRITICAL: Validate packet order BEFORE sending
     * @return true if packet should be sent, false if it should be blocked
     */
    public boolean validatePacket(Packet<?> packet) {
        if (!enabled) return true; // Allow all packets when disabled

        long now = System.currentTimeMillis();

        // Check if we're currently blocking a sequence
        if (blockingSequence) {
            if (now < blockSequenceUntil) {
                // Still blocking - check if this is a placement-related packet
                if (packet instanceof ServerboundUseItemOnPacket ||
                    packet instanceof ServerboundSwingPacket ||
                    packet instanceof ServerboundSetCarriedItemPacket) {
                    LOGGER.warn("BLOCKING PACKET (sequence blocked): {}", packet.getClass().getSimpleName());
                    return false; // Block this packet too
                }
            } else {
                // Blocking period expired
                blockingSequence = false;
                LOGGER.info("Sequence blocking expired, allowing packets again");
            }
        }

        // Check for sequence timeout
        if (inPlacementSequence && (now - sequenceStartTime) > SEQUENCE_TIMEOUT_MS) {
            LOGGER.warn("Placement sequence TIMEOUT - resetting sequence state");
            resetSequence();
        }

        // PACKET SEQUENCE VALIDATION

        // 1. UpdateSelectedSlotC2SPacket (slot switch) - STARTS sequence
        if (packet instanceof ServerboundSetCarriedItemPacket) {
            if (!inPlacementSequence) {
                // Start new placement sequence
                inPlacementSequence = true;
                slotSwitchSeen = true;
                handSwingSeen = false;
                sequenceStartTime = now;
                LOGGER.info("✓ SEQUENCE START: Slot switch");
            } else {
                // Already in sequence - this is suspicious
                LOGGER.error("[CRITICAL] Slot switch DURING active sequence! BLOCKING!");
                blockSequence(now);
                return false;
            }
        }

        // 2. HandSwingC2SPacket (swing) - Must come AFTER slot switch (if present)
        else if (packet instanceof ServerboundSwingPacket) {
            if (!inPlacementSequence) {
                // Start sequence WITHOUT slot switch (chest already in hand)
                inPlacementSequence = true;
                slotSwitchSeen = false;
                handSwingSeen = true;
                sequenceStartTime = now;
                LOGGER.info("✓ SEQUENCE START: Hand swing (no slot switch)");
            } else if (!handSwingSeen) {
                // Hand swing in sequence - this is correct
                handSwingSeen = true;
                LOGGER.info("✓ Hand swing after slot switch");
            } else {
                // Already saw hand swing - this is suspicious
                LOGGER.error("[CRITICAL] DUPLICATE hand swing in sequence! BLOCKING!");
                blockSequence(now);
                return false;
            }
        }

        // 3. PlayerInteractBlockC2SPacket (placement) - Must come LAST
        else if (packet instanceof ServerboundUseItemOnPacket) {
            if (!inPlacementSequence) {
                // Interact packet WITHOUT sequence start - INVALID!
                LOGGER.error("[CRITICAL] Interact packet WITHOUT proper sequence! BLOCKING!");
                LOGGER.error("Expected: [SlotSwitch] -> HandSwing -> Interact");
                LOGGER.error("Got: Interact (no HandSwing!)");
                blockSequence(now);
                return false;
            } else if (!handSwingSeen) {
                // Interact packet WITHOUT hand swing - INVALID!
                LOGGER.error("[CRITICAL] Interact packet WITHOUT hand swing! BLOCKING!");
                LOGGER.error("Sequence state: slotSwitch={}, handSwing={}", slotSwitchSeen, handSwingSeen);
                blockSequence(now);
                return false;
            } else {
                // VALID SEQUENCE!
                if (slotSwitchSeen) {
                    LOGGER.info("✓ COMPLETE SEQUENCE: SlotSwitch -> HandSwing -> Interact");
                } else {
                    LOGGER.info("✓ COMPLETE SEQUENCE: HandSwing -> Interact");
                }
                // Reset sequence after successful placement
                resetSequence();
            }
        }

        // Track this packet

        return true; // Allow packet to be sent
    }

    /**
     * Block the current sequence and future packets for BLOCK_DURATION_MS
     */
    private void blockSequence(long now) {
        blockingSequence = true;
        blockSequenceUntil = now + BLOCK_DURATION_MS;
        resetSequence();
    }

    /**
     * Reset sequence tracking state
     */
    private void resetSequence() {
        inPlacementSequence = false;
        slotSwitchSeen = false;
        handSwingSeen = false;
        sequenceStartTime = 0;
    }

    /**
     * Reset all tracking state
     */
    public void reset() {
        blockingSequence = false;
        blockSequenceUntil = 0;
        resetSequence();
    }
}
