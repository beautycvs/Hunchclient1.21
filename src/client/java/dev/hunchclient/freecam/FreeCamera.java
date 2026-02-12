package dev.hunchclient.freecam;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.CommonListenerCookie;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.ServerLinks;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;

import java.util.Collections;
import java.util.UUID;

/**
 * A fake player entity that acts as a free-floating camera.
 * This entity exists only on the client and does not send packets to the server.
 */
public class FreeCamera extends LocalPlayer {

    private static final Minecraft MC = Minecraft.getInstance();

    private static ClientPacketListener createNetworkHandler() {
        return new ClientPacketListener(
                MC,
                new net.minecraft.network.Connection(PacketFlow.CLIENTBOUND),
                new CommonListenerCookie(
                        new LevelLoadTracker(),
                        new GameProfile(UUID.randomUUID(), "FreeCamera"),
                        MC.getTelemetryManager().createWorldSessionManager(false, null, null),
                        MC.player.registryAccess().freeze(),
                        FeatureFlagSet.of(),
                        null,
                        null,
                        null,
                        Collections.emptyMap(),
                        null,
                        Collections.emptyMap(),
                        ServerLinks.EMPTY,
                        Collections.emptyMap(),
                        false)) {
            @Override
            public void send(Packet<?> packet) {
                // Don't send any packets - this is a fake entity
            }
        };
    }

    private final double horizontalSpeed;
    private final double verticalSpeed;
    private final boolean noClip;

    public FreeCamera(int id, double horizontalSpeed, double verticalSpeed, boolean noClip) {
        super(MC, MC.level, createNetworkHandler(), MC.player.getStats(), MC.player.getRecipeBook(), Input.EMPTY, false);

        this.horizontalSpeed = horizontalSpeed;
        this.verticalSpeed = verticalSpeed;
        this.noClip = noClip;

        setId(id);
        setPose(Pose.SWIMMING);
        getAbilities().flying = true;
        input = new KeyboardInput(MC.options);
    }

    @Override
    public void copyPosition(Entity entity) {
        applyPosition(new FreecamPosition(entity));
    }

    public void applyPosition(FreecamPosition position) {
        snapTo(position.x, position.y, position.z, position.yaw, position.pitch);
        xBob = getXRot();
        yBob = getYRot();
        xBobO = xBob;
        yBobO = yBob;
    }

    public void spawn() {
        ((ClientLevel) level()).addEntity(this);
    }

    public void despawn() {
        ((ClientLevel) level()).removeEntity(getId(), RemovalReason.DISCARDED);
    }

    @Override
    protected void checkFallDamage(double heightDifference, boolean onGround, BlockState landedState, BlockPos landedPosition) {
        // No fall damage for freecam
    }

    @Override
    public float getAttackAnim(float tickDelta) {
        return MC.player.getAttackAnim(tickDelta);
    }

    @Override
    public int getUseItemRemainingTicks() {
        return MC.player.getUseItemRemainingTicks();
    }

    @Override
    public boolean isUsingItem() {
        return MC.player.isUsingItem();
    }

    @Override
    public boolean onClimbable() {
        return false;
    }

    @Override
    public boolean isInWater() {
        return false;
    }

    @Override
    public MobEffectInstance getEffect(Holder<MobEffect> holder) {
        return MC.player.getEffect(holder);
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return noClip ? PushReaction.IGNORE : PushReaction.NORMAL;
    }

    @Override
    public boolean canCollideWith(Entity other) {
        return false;
    }

    @Override
    public void setPose(Pose pose) {
        super.setPose(Pose.SWIMMING);
    }

    @Override
    public boolean isMovingSlowly() {
        return false;
    }

    @Override
    protected boolean updateIsUnderwater() {
        this.wasUnderwater = this.isEyeInFluid(FluidTags.WATER);
        return this.wasUnderwater;
    }

    @Override
    protected void doWaterSplashEffect() {
        // No splash effects
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    public void aiStep() {
        // Set noClip BEFORE super.aiStep() so collisions are ignored
        if (noClip) {
            noPhysics = true;
        }

        getAbilities().setFlyingSpeed(0);
        FreecamMotion.doMotion(this, horizontalSpeed, verticalSpeed);
        super.aiStep();
        getAbilities().flying = true;
        setOnGround(false);
    }

    @Override
    public void tick() {
        // Apply noClip at the start of tick as well
        if (noClip) {
            noPhysics = true;
        }
        super.tick();
    }

    @Override
    public boolean isSpectator() {
        // Return true to enable spectator-like behavior (see through blocks, no collision rendering)
        return noClip;
    }
}
