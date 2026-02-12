package dev.hunchclient.mixin.client;

import dev.hunchclient.mixininterface.IHiddenEntityMarker;
import dev.hunchclient.module.impl.dungeons.StarredMobsESPModule;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into EntityRenderer to mark EntityRenderStates that should be hidden
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin<T extends Entity, S extends EntityRenderState> {

    /**
     * Mark entities to skip during updateRenderState (we have entity access here)
     */
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void hunchclient$markHiddenEntity(T entity, S state, float tickDelta, CallbackInfo ci) {
        if (state instanceof IHiddenEntityMarker) {
            StarredMobsESPModule module = StarredMobsESPModule.getInstance();
            if (module != null) {
                boolean shouldHide = module.shouldHideEntity(entity);
                ((IHiddenEntityMarker) state).hunchclient$setHidden(shouldHide);
            }
        }
    }

    /**
     * Mixin to add hidden marker field to EntityRenderState
     */
    @Mixin(EntityRenderState.class)
    public static class EntityRenderStateMixin implements IHiddenEntityMarker {
        @Unique
        private boolean hunchclient$hidden = false;

        @Override
        public boolean hunchclient$isHidden() {
            return this.hunchclient$hidden;
        }

        @Override
        public void hunchclient$setHidden(boolean hidden) {
            this.hunchclient$hidden = hidden;
        }
    }
}
