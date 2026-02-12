package dev.hunchclient.bridge.module;

import net.minecraft.core.BlockPos;

public interface ISecretHitboxes {
    boolean isEnabled();
    boolean isButtonEnabled();
    boolean isChestsEnabled();
    boolean isLeverEnabled();
    boolean isEssence(BlockPos pos);
}
