package dev.hunchclient.render.primitive;

import com.mojang.blaze3d.vertex.BufferBuilder;
import dev.hunchclient.render.Renderer;
import dev.hunchclient.render.SkyblockerRenderPipelines;
import dev.hunchclient.render.state.CameraRenderState;
import dev.hunchclient.render.state.LinesRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Lines renderer
 * CRITICAL: RenderPipelines.LINES uses DrawMode.LINES which requires PAIRS of vertices!
 */
public final class LinesRenderer implements PrimitiveRenderer<LinesRenderState> {
    public static final LinesRenderer INSTANCE = new LinesRenderer();

    private LinesRenderer() {}

    @Override
    public void submitPrimitives(LinesRenderState state, CameraRenderState cameraState) {
        Vec3[] points = state.points;
        BufferBuilder buffer = Renderer.getBuffer(state.throughWalls ? SkyblockerRenderPipelines.LINES_THROUGH_WALLS : RenderPipelines.LINES, state.lineWidth);
        Matrix4f positionMatrix = new Matrix4f()
                .translate((float) -cameraState.pos.x, (float) -cameraState.pos.y, (float) -cameraState.pos.z);

        // LINES DrawMode requires PAIRS of vertices for each segment
        // For points [A, B, C, D], we render: [A,B], [B,C], [C,D]
        for (int i = 0; i < points.length - 1; i++) {
            Vec3 current = points[i];
            Vec3 next = points[i + 1];
            
            // Calculate normal vector from current to next
            Vector3f normalVec = next.toVector3f()
                    .sub((float) current.x, (float) current.y, (float) current.z)
                    .normalize();

            // Submit START vertex of this segment
            buffer.addVertex(positionMatrix, (float) current.x, (float) current.y, (float) current.z)
                    .setColor(state.colourComponents[0], state.colourComponents[1], state.colourComponents[2], state.alpha)
                    .setNormal(normalVec.x(), normalVec.y(), normalVec.z());

            // Submit END vertex of this segment  
            buffer.addVertex(positionMatrix, (float) next.x, (float) next.y, (float) next.z)
                    .setColor(state.colourComponents[0], state.colourComponents[1], state.colourComponents[2], state.alpha)
                    .setNormal(normalVec.x(), normalVec.y(), normalVec.z());
        }
    }
}
