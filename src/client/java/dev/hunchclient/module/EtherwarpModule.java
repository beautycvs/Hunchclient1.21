package dev.hunchclient.module;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class EtherwarpModule {

    private static final Minecraft MC = Minecraft.getInstance();

    private static final Set<String> ETHERWARP_ITEM_IDS = Set.of(
        "ETHERWARP_CONDUIT",
        "ETHERWARP_TRANSMITTER",
        "ETHERWARP_MERGER"
    );

    private static final Set<String> NAME_HINTS = Set.of(
        "etherwarp conduit",
        "etherwarp transmitter",
        "etherwarp merger",
        "etherwarp"
    );

    private static final Set<Integer> PASSABLE_BLOCK_IDS = new HashSet<>(Arrays.asList(
        0, 31, 32, 37, 38, 39, 40, 50, 51, 55, 59, 63, 64, 65, 66, 68, 69, 70, 71, 72, 75,
        76, 77, 78, 83, 85, 90, 93, 94, 96, 101, 102, 104, 105, 106, 107, 111, 113, 131,
        132, 139, 140, 141, 142, 143, 144, 145, 147, 148, 149, 150, 151, 157, 158, 171,
        175, 183, 184, 185, 186, 187, 188, 189, 190, 191, 192, 193, 194, 195, 196, 197
    ));

    private EtherwarpModule() {
    }

    public static boolean holdingEtherwarpItem() {
        if (MC.player == null) {
            return false;
        }
        return isEtherwarpStack(MC.player.getMainHandItem())
            || isEtherwarpStack(MC.player.getOffhandItem());
    }

    public static int getEtherwarpRange() {
        if (MC.player == null || MC.level == null) {
            return 57;
        }

        ItemStack stack = MC.player.getMainHandItem();
        if (stack.isEmpty()) {
            stack = MC.player.getOffhandItem();
            if (stack.isEmpty()) {
                return 57;
            }
        }

        CompoundTag attributes = getExtraAttributes(stack);
        if (attributes == null) {
            return 57;
        }

        int tunedTransmission = attributes.getByte("tuned_transmission")
            .map(Byte::intValue)
            .orElse(0);
        tunedTransmission = Math.max(tunedTransmission, attributes.getInt("ether_transmission").orElse(0));

        return 57 + tunedTransmission;
    }

    public static BlockPos getLookingAtBlock() {
        if (MC.player == null || MC.level == null) {
            return null;
        }

        double range = getEtherwarpRange();
        Vec3 eyePos = MC.player.getEyePosition();
        Vec3 lookVec = MC.player.getViewVector(1.0f);
        Vec3 traceEnd = eyePos.add(lookVec.scale(range));

        BlockHitResult result = MC.level.clip(new ClipContext(
            eyePos,
            traceEnd,
            ClipContext.Block.OUTLINE,
            ClipContext.Fluid.NONE,
            MC.player
        ));

        if (result != null && result.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = result.getBlockPos();
            BlockState state = MC.level.getBlockState(pos);
            if (!state.isAir()) {
                return pos;
            }
        }
        return null;
    }

    public static boolean isBlockEtherwarpable(BlockPos pos) {
        if (MC.level == null || pos == null) {
            return false;
        }

        BlockState targetBlock = MC.level.getBlockState(pos);
        if (targetBlock.isAir()) {
            return false;
        }

        BlockPos aboveOne = pos.above();
        BlockPos aboveTwo = pos.above(2);

        BlockState air1 = MC.level.getBlockState(aboveOne);
        BlockState air2 = MC.level.getBlockState(aboveTwo);

        return isBlockPassable(air1, aboveOne) && isBlockPassable(air2, aboveTwo);
    }

    public static boolean canEtherwarp() {
        if (MC.player == null || MC.level == null) {
            return false;
        }
        if (MC.player.isDeadOrDying()) {
            return false;
        }
        if (MC.screen != null) {
            String name = MC.screen.getClass().getSimpleName();
            if (!"ChatScreen".equals(name)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isEtherwarpStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        try {
            // Check if it's a shovel or sword (etherwarp items are always shovels/swords)
            String itemName = stack.getItem().toString().toLowerCase(Locale.ROOT);
            boolean isShovelOrSword = itemName.contains("shovel") || itemName.contains("sword");

            if (!isShovelOrSword) {
                return false;
            }


            // Check NBT data for Skyblock ID
            CompoundTag attributes = getExtraAttributes(stack);
            if (attributes != null) {
                String id = attributes.getString("id").orElse("");
                if (!id.isEmpty()) {
                    // Direct etherwarp items
                    if (ETHERWARP_ITEM_IDS.contains(id)) {
                        return true;
                    }
                    // AOTV/AOTE with ethermerge
                    if (("ASPECT_OF_THE_VOID".equals(id) || "ASPECT_OF_THE_END".equals(id))
                        && attributes.getByte("ethermerge").orElse((byte) 0) == 1) {
                        return true;
                    }
                }
            } else {
            }

            // Fallback: check display name for "etherwarp"
            String displayName = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
            if (NAME_HINTS.stream().anyMatch(displayName::contains)) {
                return true;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private static CompoundTag getExtraAttributes(ItemStack stack) {
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
                    // Try direct access without wrapper
                    if (nbt.contains("id")) {
                        return nbt;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static boolean isBlockPassable(BlockState state, BlockPos position) {
        if (state.isAir()) {
            return true;
        }
        try {
            int rawId = Block.getId(state);
            if (PASSABLE_BLOCK_IDS.contains(rawId)) {
                return true;
            }
            return !state.isRedstoneConductor(MC.level, position);
        } catch (Exception e) {
            return false;
        }
    }
}
