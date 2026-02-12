// Decompiled with: CFR 0.152
// Class Version: 21
package dev.hunchclient.module.impl.dungeons;

import java.util.BitSet;
import java.util.Locale;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
// block imports below
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DryVegetationBlock;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SeagrassBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.SmallDripleafBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.TallFlowerBlock;
import net.minecraft.world.level.block.TallGrassBlock;
import net.minecraft.world.level.block.TallSeagrassBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.TripWireBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.WallSkullBlock;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.level.block.WoolCarpetBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

@Environment(value=EnvType.CLIENT)
public class EtherwarpModule {
    private static final Minecraft MC = Minecraft.getInstance();
    private static final Set<String> ETHERWARP_ITEM_IDS = Set.of("ETHERWARP_CONDUIT", "ETHERWARP_TRANSMITTER", "ETHERWARP_MERGER");
    private static final Set<String> NAME_HINTS = Set.of("etherwarp conduit", "etherwarp transmitter", "etherwarp merger", "etherwarp");

    /**
     * BitSet for fast O(1) lookup of passable blocks.
     * Uses block type classes instead of hardcoded IDs for version independence.
     */
    private static final BitSet PASSABLE_BLOCK_IDS = initPassableBlocks();

    private EtherwarpModule() {
    }

    /**
     * Initialize the BitSet with all passable block types.
     * Keep this list intact; it defines which blocks etherwarp treats as passable.
     */
    private static BitSet initPassableBlocks() {
        BitSet bitSet = new BitSet();

        // Block types considered passable for etherwarp pathing
        Class<?>[] passableTypes = {
            ButtonBlock.class,
            CarpetBlock.class,
            SkullBlock.class,
            WallSkullBlock.class,
            LadderBlock.class,
            SaplingBlock.class,
            FlowerBlock.class,
            StemBlock.class,
            CropBlock.class,
            RailBlock.class,
            SnowLayerBlock.class,
            TripWireBlock.class,
            TripWireHookBlock.class,
            FireBlock.class,
            AirBlock.class,
            TorchBlock.class,
            FlowerPotBlock.class,
            TallFlowerBlock.class,
            TallGrassBlock.class,
            BushBlock.class,
            SeagrassBlock.class,
            TallSeagrassBlock.class,
            SugarCaneBlock.class,
            LiquidBlock.class,
            VineBlock.class,
            MushroomBlock.class,
            PistonHeadBlock.class,
            WoolCarpetBlock.class,
            WebBlock.class,
            DryVegetationBlock.class,
            SmallDripleafBlock.class,
            LeverBlock.class,
            NetherWartBlock.class,
            NetherPortalBlock.class,
            RedStoneWireBlock.class,
            ComparatorBlock.class,
            RedstoneTorchBlock.class,
            RepeaterBlock.class
        };

        // Iterate through all registered blocks and mark passable ones
        for (Block block : BuiltInRegistries.BLOCK) {
            for (Class<?> passableType : passableTypes) {
                if (passableType.isInstance(block)) {
                    int rawId = Block.getId(block.defaultBlockState());
                    bitSet.set(rawId);
                    break;
                }
            }
        }

        return bitSet;
    }

    public static boolean holdingEtherwarpItem() {
        if (EtherwarpModule.MC.player == null) {
            return false;
        }
        return EtherwarpModule.isEtherwarpStack(EtherwarpModule.MC.player.getMainHandItem()) || EtherwarpModule.isEtherwarpStack(EtherwarpModule.MC.player.getOffhandItem());
    }

    public static int getEtherwarpRange() {
        if (EtherwarpModule.MC.player == null || EtherwarpModule.MC.level == null) {
            return 57;
        }
        ItemStack stack = EtherwarpModule.MC.player.getMainHandItem();
        if (stack.isEmpty() && (stack = EtherwarpModule.MC.player.getOffhandItem()).isEmpty()) {
            return 57;
        }
        CompoundTag attributes = EtherwarpModule.getExtraAttributes(stack);
        if (attributes == null) {
            return 57;
        }
        int tunedTransmission = attributes.getByte("tuned_transmission").map(Byte::intValue).orElse(0);
        tunedTransmission = Math.max(tunedTransmission, attributes.getInt("ether_transmission").orElse(0));
        return 57 + tunedTransmission;
    }

    /**
     * Result of etherwarp position calculation
     */
    public static class EtherPos {
        public final boolean succeeded;
        public final BlockPos pos;
        public final BlockState state;

        public EtherPos(boolean succeeded, BlockPos pos, BlockState state) {
            this.succeeded = succeeded;
            this.pos = pos;
            this.state = state;
        }

        public static final EtherPos NONE = new EtherPos(false, null, null);
    }

    /**
     * Get the etherwarp target position using voxel traversal.
     * This is the accurate method that matches actual etherwarp behavior.
     */
    public static EtherPos getEtherPos(Vec3 position, double distance, boolean returnEnd, boolean etherWarp) {
        if (MC.player == null || position == null) {
            return EtherPos.NONE;
        }

        // Calculate eye height based on current sneaking state
        double eyeHeight = MC.player.isShiftKeyDown() ? 1.54 : 1.62;
        Vec3 startPos = position.add(0, eyeHeight, 0);

        // For raytrace, extend much further than etherwarp range to always find a block
        // but track the actual etherwarp range to determine if it's valid
        double raytraceDistance = 200.0; // Extended raytrace distance
        Vec3 endPos = MC.player.getViewVector(1.0f).scale(raytraceDistance).add(startPos);

        EtherPos result = traverseVoxels(startPos, endPos, etherWarp, distance);
        if (result != EtherPos.NONE || !returnEnd) {
            return result;
        }
        return new EtherPos(false, BlockPos.containing(endPos), null);
    }

    /**
     * Traverses voxels from start to end using DDA algorithm.
     * Returns the first valid etherwarp position or NONE.
     * Based on Bloom's implementation.
     *
     * @param start Starting position (player eye position)
     * @param end End position of raytrace (extended beyond etherwarp range)
     * @param etherWarp Whether this is for etherwarp (true) or other purposes
     * @param etherwarpRange The actual etherwarp range - blocks outside this are marked as failed
     */
    private static EtherPos traverseVoxels(Vec3 start, Vec3 end, boolean etherWarp, double etherwarpRange) {
        double x0 = start.x, y0 = start.y, z0 = start.z;
        double x1 = end.x, y1 = end.y, z1 = end.z;

        int x = (int) Math.floor(x0);
        int y = (int) Math.floor(y0);
        int z = (int) Math.floor(z0);

        int endX = (int) Math.floor(x1);
        int endY = (int) Math.floor(y1);
        int endZ = (int) Math.floor(z1);

        double dirX = x1 - x0;
        double dirY = y1 - y0;
        double dirZ = z1 - z0;

        int stepX = (int) Math.signum(dirX);
        int stepY = (int) Math.signum(dirY);
        int stepZ = (int) Math.signum(dirZ);

        double invDirX = dirX != 0.0 ? 1.0 / dirX : Double.MAX_VALUE;
        double invDirY = dirY != 0.0 ? 1.0 / dirY : Double.MAX_VALUE;
        double invDirZ = dirZ != 0.0 ? 1.0 / dirZ : Double.MAX_VALUE;

        double tDeltaX = Math.abs(invDirX * stepX);
        double tDeltaY = Math.abs(invDirY * stepY);
        double tDeltaZ = Math.abs(invDirZ * stepZ);

        double tMaxX = Math.abs((x + Math.max(stepX, 0) - x0) * invDirX);
        double tMaxY = Math.abs((y + Math.max(stepY, 0) - y0) * invDirY);
        double tMaxZ = Math.abs((z + Math.max(stepZ, 0) - z0) * invDirZ);

        for (int i = 0; i < 1000; i++) {
            BlockPos blockPos = new BlockPos(x, y, z);

            if (MC.level == null) {
                return EtherPos.NONE;
            }

            BlockState currentBlock = MC.level.getBlockState(blockPos);
            int currentBlockId = Block.getId(currentBlock);

            // Check if this block is solid (not passable)
            boolean isSolid = !PASSABLE_BLOCK_IDS.get(currentBlockId);

            if ((isSolid && etherWarp) || (currentBlockId != 0 && !etherWarp)) {
                if (!etherWarp && PASSABLE_BLOCK_IDS.get(currentBlockId)) {
                    return new EtherPos(false, blockPos, currentBlock);
                }

                // Check if player can fit (foot block at +1 and head block at +2 must be passable)
                BlockPos footPos = blockPos.above(1);
                BlockState footBlock = MC.level.getBlockState(footPos);
                int footBlockId = Block.getId(footBlock);

                if (!PASSABLE_BLOCK_IDS.get(footBlockId)) {
                    return new EtherPos(false, blockPos, currentBlock);
                }

                BlockPos headPos = blockPos.above(2);
                BlockState headBlock = MC.level.getBlockState(headPos);
                int headBlockId = Block.getId(headBlock);

                if (!PASSABLE_BLOCK_IDS.get(headBlockId)) {
                    return new EtherPos(false, blockPos, currentBlock);
                }

                // Block is valid for warping - now check if it's within etherwarp range
                // Calculate distance from start position to the block center
                Vec3 blockCenter = new Vec3(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5);
                double distanceToBlock = start.distanceTo(blockCenter);

                // succeeded=true only if within range, but always return the position for overlay
                boolean withinRange = distanceToBlock <= etherwarpRange;
                return new EtherPos(withinRange, blockPos, currentBlock);
            }

            if (x == endX && y == endY && z == endZ) {
                return EtherPos.NONE;
            }

            // DDA step
            if (tMaxX <= tMaxY && tMaxX <= tMaxZ) {
                tMaxX += tDeltaX;
                x += stepX;
            } else if (tMaxY <= tMaxZ) {
                tMaxY += tDeltaY;
                y += stepY;
            } else {
                tMaxZ += tDeltaZ;
                z += stepZ;
            }
        }

        return EtherPos.NONE;
    }

    /**
     * Get the etherwarp result using accurate voxel traversal.
     * Returns the full EtherPos with success status.
     */
    public static EtherPos getEtherwarpResult() {
        if (MC.player == null || MC.level == null) {
            return EtherPos.NONE;
        }
        double range = getEtherwarpRange();
        Vec3 playerPos = new Vec3(MC.player.getX(), MC.player.getY(), MC.player.getZ());
        return getEtherPos(playerPos, range, true, true); // returnEnd = true to always get a position
    }

    /**
     * Get the etherwarp result AS IF the player is sneaking.
     * Used by LeftClickEtherwarp which will sneak before warping.
     */
    public static EtherPos getEtherwarpResultSneaking() {
        if (MC.player == null || MC.level == null) {
            return EtherPos.NONE;
        }
        double range = getEtherwarpRange();
        Vec3 playerPos = new Vec3(MC.player.getX(), MC.player.getY(), MC.player.getZ());

        // Force sneak eye height (1.54)
        Vec3 startPos = playerPos.add(0, 1.54, 0);
        double raytraceDistance = 200.0;
        Vec3 endPos = MC.player.getViewVector(1.0f).scale(raytraceDistance).add(startPos);

        EtherPos result = traverseVoxels(startPos, endPos, true, range);
        if (result != EtherPos.NONE) {
            return result;
        }
        return new EtherPos(false, BlockPos.containing(endPos), null);
    }

    /**
     * Get the looking at block using the accurate voxel traversal method.
     * Only returns the position if it's warpable.
     */
    public static BlockPos getLookingAtBlock() {
        EtherPos result = getEtherwarpResult();
        return result.succeeded ? result.pos : null;
    }

    /**
     * Check if a block position is etherwarpable using the accurate voxel traversal.
     */
    public static boolean isBlockEtherwarpable(BlockPos pos) {
        if (pos == null) {
            return false;
        }
        EtherPos result = getEtherwarpResult();
        return result.succeeded && pos.equals(result.pos);
    }

    public static boolean canEtherwarp() {
        String name;
        if (EtherwarpModule.MC.player == null || EtherwarpModule.MC.level == null) {
            return false;
        }
        if (EtherwarpModule.MC.player.isShiftKeyDown()) {
            return false;
        }
        return EtherwarpModule.MC.screen == null || "ChatScreen".equals(name = EtherwarpModule.MC.screen.getClass().getSimpleName());
    }

    private static boolean isEtherwarpStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        try {
            String id;
            boolean isShovelOrSword;
            String itemName = stack.getItem().toString().toLowerCase(Locale.ROOT);
            boolean bl = isShovelOrSword = itemName.contains("shovel") || itemName.contains("sword");
            if (!isShovelOrSword) {
                return false;
            }
            CompoundTag attributes = EtherwarpModule.getExtraAttributes(stack);
            if (attributes != null && attributes.contains("id")) {
                id = attributes.getString("id").orElse("");
                if (!id.isEmpty() && ETHERWARP_ITEM_IDS.contains(id)) {
                    return true;
                }
                if (!id.isEmpty() && ("ASPECT_OF_THE_VOID".equals(id) || "ASPECT_OF_THE_END".equals(id)) && attributes.getByte("ethermerge").orElse((byte)0) == 1) {
                    return true;
                }
            }
            String displayName = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
            if (NAME_HINTS.stream().anyMatch(displayName::contains)) {
                return true;
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private static CompoundTag getExtraAttributes(ItemStack stack) {
        try {
            CompoundTag nbt;
            CustomData customData = (CustomData)stack.get(DataComponents.CUSTOM_DATA);
            if (customData != null && (nbt = customData.copyTag()) != null) {
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
        catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Checks if a block is passable for etherwarp (player can stand in it).
     * Uses optimized BitSet lookup for O(1) performance.
     *
     * @param state The block state to check
     * @param position The position of the block
     * @return true if the block is passable, false otherwise
     */
    private static boolean isBlockPassable(BlockState state, BlockPos position) {
        if (state.isAir()) {
            return true;
        }
        try {
            int rawId = Block.getId(state);
            // Use BitSet.get() for O(1) lookup
            if (PASSABLE_BLOCK_IDS.get(rawId)) {
                return true;
            }
            // Fallback: check if block has full collision box
            return !state.isCollisionShapeFullBlock((BlockGetter)EtherwarpModule.MC.level, position);
        }
        catch (Exception e) {
            return false;
        }
    }
}
