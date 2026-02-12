package dev.hunchclient.render.primitive;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.hunchclient.render.MatrixHelper;
import dev.hunchclient.render.Renderer;
import dev.hunchclient.render.SkyblockerRenderPipelines;
import dev.hunchclient.render.state.CameraRenderState;
import dev.hunchclient.render.state.OutlinedBoxRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.ShapeRenderer;
import org.joml.Matrix4f;

/**
 * Outlined box renderer - 1:1 from Skyblocker
 */
public final class OutlinedBoxRenderer implements PrimitiveRenderer<OutlinedBoxRenderState> {
    public static final OutlinedBoxRenderer INSTANCE = new OutlinedBoxRenderer();

    private OutlinedBoxRenderer() {}

    @Override
    public void submitPrimitives(OutlinedBoxRenderState state, CameraRenderState cameraState) {
        BufferBuilder buffer = Renderer.getBuffer(state.throughWalls ? SkyblockerRenderPipelines.LINES_THROUGH_WALLS : RenderPipelines.LINES, state.lineWidth);
        Matrix4f positionMatrix = new Matrix4f()
            .translate((float) -cameraState.pos.x, (float) -cameraState.pos.y, (float) -cameraState.pos.z);
        PoseStack matrices = MatrixHelper.toStack(positionMatrix);

        ShapeRenderer.renderLineBox(
            matrices.last(),
            buffer,
            state.minX,
            state.minY,
            state.minZ,
            state.maxX,
            state.maxY,
            state.maxZ,
            state.colourComponents[0],
            state.colourComponents[1],
            state.colourComponents[2],
            state.alpha
        );
    }
}
