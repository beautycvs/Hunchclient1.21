package dev.hunchclient.render;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;

/**
 * Thin wrapper around a few matrix utilities we need for world rendering.
 * Copied from Skyblocker to avoid sharing mutable matrices between primitives.
 */
public interface MatrixHelper {

    /**
     * Copies the supplied {@link Matrix4f} into a brand new instance so that
     * later translations/rotations do not clobber shared state.
     */
    static Matrix4f copyOf(Matrix4f matrix) {
        return new Matrix4f(matrix);
    }

    /**
     * Builds a fresh {@link PoseStack} and seeds its position matrix with the
     * provided {@link Matrix4f}. Useful when VertexRendering APIs expect stacks.
     */
    static PoseStack toStack(Matrix4f positionMatrix) {
        PoseStack matrices = new PoseStack();
        matrices.last().pose().set(positionMatrix);
        return matrices;
    }
}
