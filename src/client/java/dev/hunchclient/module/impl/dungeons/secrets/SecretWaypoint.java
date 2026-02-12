
/*
* Secret Routes Mod - Secret Route Waypoints for Hypixel Skyblock Dungeons
 * Copyright 2025 yourboykyle & R-aMcC
 *
 * <DO NOT REMOVE THIS COPYRIGHT NOTICE>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package dev.hunchclient.module.impl.dungeons.secrets;

import dev.hunchclient.render.RenderContext;
import dev.hunchclient.util.Renderable;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Secret Waypoint - Simplified port from Skyblocker
 * Represents a secret location in a dungeon room
 */
public class SecretWaypoint implements Renderable {
    static final List<String> SECRET_ITEMS = List.of("Candycomb", "Decoy", "Defuse Kit", "Dungeon Chest Key", "Healing VIII", "Inflatable Jerry", "Spirit Leap", "Training Weights", "Trap", "Treasure Talisman");

    public final int secretIndex;
    public final Category category;
    public final Component name;
    public final BlockPos pos;
    protected final Vec3 centerPos;
    private boolean found = false;
    private boolean missing = false;

    SecretWaypoint(int secretIndex, JsonObject waypoint, String name, BlockPos pos) {
        this(secretIndex, Category.get(waypoint), Component.nullToEmpty(name), pos);
    }

    SecretWaypoint(int secretIndex, Category category, String name, BlockPos pos) {
        this(secretIndex, category, Component.nullToEmpty(name), pos);
    }

    public SecretWaypoint(int secretIndex, Category category, Component name, BlockPos pos) {
        this.secretIndex = secretIndex;
        this.category = category;
        this.name = name;
        this.pos = pos;
        this.centerPos = Vec3.atCenterOf(pos);
    }

    static ToDoubleFunction<SecretWaypoint> getSquaredDistanceToFunction(Entity entity) {
        return secretWaypoint -> entity.distanceToSqr(secretWaypoint.centerPos);
    }

    static Predicate<SecretWaypoint> getRangePredicate(Entity entity) {
        return secretWaypoint -> new Vec3(entity.getX(), entity.getY(), entity.getZ()).closerThan(secretWaypoint.centerPos, 16);
    }

    public boolean shouldRender() {
        return !found && category.isEnabled();
    }

    boolean needsInteraction() {
        return category.needsInteraction();
    }

    boolean isLever() {
        return category.isLever();
    }

    boolean needsItemPickup() {
        return category.needsItemPickup();
    }

    boolean isBat() {
        return category.isBat();
    }

    boolean isEnabled() {
        return category.isEnabled();
    }

    public void setFound() {
        this.found = true;
        this.missing = false;
    }

    public void setMissing() {
        this.missing = true;
        this.found = false;
    }

    public boolean isFound() {
        return found;
    }

    public boolean isMissing() {
        return missing;
    }

    public String getName() {
        return name.getString();
    }

    @Override
    public void extractRendering(RenderContext context) {
        // TODO: Implement waypoint rendering
        // For now just a placeholder
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || obj instanceof SecretWaypoint other &&
               secretIndex == other.secretIndex &&
               category == other.category &&
               pos.equals(other.pos);
    }

    @Override
    public int hashCode() {
        return Objects.hash(secretIndex, category, pos);
    }

    @NotNull
    SecretWaypoint relativeToActual(Room room) {
        return new SecretWaypoint(secretIndex, category, name, room.relativeToActual(pos));
    }

    public enum Category implements StringRepresentable {
        ENTRANCE("entrance", true, 0, 255, 0),
        SUPERBOOM("superboom", true, 255, 0, 0),
        CHEST("chest", true, 2, 213, 250),
        ITEM("item", true, 2, 64, 250),
        BAT("bat", true, 142, 66, 0),
        WITHER("wither", true, 30, 30, 30),
        LEVER("lever", true, 250, 217, 2),
        FAIRYSOUL("fairysoul", true, 255, 85, 255),
        STONK("stonk", true, 146, 52, 235),
        AOTV("aotv", true, 252, 98, 3),
        PEARL("pearl", true, 57, 117, 125),
        PRINCE("prince", true, 133, 21, 13),
        MINEPOINT("minepoint", true, 255, 165, 0), // NEW: Orange for mine points
        EXITROUTE("exitroute", true, 0, 255, 128), // NEW: Green for exit routes
        DEFAULT("default", true, 190, 255, 252);

        private static final Codec<Category> CODEC = StringRepresentable.fromEnum(Category::values);
        private final String name;
        private final boolean enabled;
        public final float[] colorComponents;

        Category(String name, boolean enabled, int... intColorComponents) {
            this.name = name;
            this.enabled = enabled;
            colorComponents = new float[intColorComponents.length];
            for (int i = 0; i < intColorComponents.length; i++) {
                colorComponents[i] = intColorComponents[i] / 255F;
            }
        }

        static Category get(JsonObject waypointJson) {
            try {
                String catStr = waypointJson.get("category").getAsString();
                return valueOf(catStr.toUpperCase());
            } catch (Exception e) {
                return DEFAULT;
            }
        }

        boolean needsInteraction() {
            return this == CHEST || this == WITHER;
        }

        boolean isLever() {
            return this == LEVER;
        }

        boolean needsItemPickup() {
            return this == ITEM;
        }

        boolean isBat() {
            return this == BAT;
        }

        boolean isEnabled() {
            return enabled; // Simplified - always enabled for now
        }

        @Override
        public String toString() {
            return name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }
}
