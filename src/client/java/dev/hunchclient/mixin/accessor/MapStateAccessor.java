package dev.hunchclient.mixin.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * Accessor for MapState to get decorations (player markers on dungeon map)
 */
@Mixin(MapItemSavedData.class)
public interface MapStateAccessor {

    @Accessor
    Map<String, MapDecoration> getDecorations();
}
