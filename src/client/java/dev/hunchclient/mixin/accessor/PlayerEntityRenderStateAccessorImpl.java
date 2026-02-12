package dev.hunchclient.mixin.accessor;

import dev.hunchclient.accessor.PlayerEntityRenderStateAccessor;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Implementation mixin to add actual player name storage to PlayerEntityRenderState
 */
@Mixin(AvatarRenderState.class)
public class PlayerEntityRenderStateAccessorImpl implements PlayerEntityRenderStateAccessor {

    @Unique
    private String hunchclient$actualPlayerName;

    @Override
    public void hunchclient$setActualPlayerName(String name) {
        this.hunchclient$actualPlayerName = name;
    }

    @Override
    public String hunchclient$getActualPlayerName() {
        return this.hunchclient$actualPlayerName;
    }
}
