package dev.hunchclient.render.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * External Vertex Renderer - wrapper around VertexRenderer
 * For backward compatibility with examples
 */
public class ExternalVertexRenderer {

    public static void drawBox(PoseStack matrices, VertexConsumer consumer,
                               double x1, double y1, double z1,
                               double x2, double y2, double z2,
                               float red, float green, float blue, float alpha) {
        VertexRenderer.drawBox(matrices, consumer, x1, y1, z1, x2, y2, z2, red, green, blue, alpha);
    }

    public static void drawBox(PoseStack matrices, VertexConsumer consumer, AABB box,
                               float red, float green, float blue, float alpha) {
        VertexRenderer.drawBox(matrices, consumer, box, red, green, blue, alpha);
    }

    public static void drawFilledBox(PoseStack matrices, VertexConsumer consumer,
                                     double x1, double y1, double z1,
                                     double x2, double y2, double z2,
                                     float red, float green, float blue, float alpha) {
        VertexRenderer.drawFilledBox(matrices, consumer, x1, y1, z1, x2, y2, z2, red, green, blue, alpha);
    }

    public static void drawFilledBox(PoseStack matrices, VertexConsumer consumer, AABB box,
                                     float red, float green, float blue, float alpha) {
        VertexRenderer.drawFilledBox(matrices, consumer, box, red, green, blue, alpha);
    }

    public static void drawLine(PoseStack matrices, VertexConsumer consumer,
                               double x1, double y1, double z1,
                               double x2, double y2, double z2,
                               float red, float green, float blue, float alpha) {
        VertexRenderer.drawLine(matrices, consumer, x1, y1, z1, x2, y2, z2, red, green, blue, alpha);
    }
}
