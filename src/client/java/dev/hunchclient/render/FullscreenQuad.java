/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.fabricmc.api.EnvType
 *  net.fabricmc.api.Environment
 *  org.lwjgl.BufferUtils
 *  org.lwjgl.opengl.GL11
 *  org.lwjgl.opengl.GL15
 *  org.lwjgl.opengl.GL20
 *  org.lwjgl.opengl.GL30
 */
package dev.hunchclient.render;

import java.nio.FloatBuffer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

@Environment(value=EnvType.CLIENT)
public final class FullscreenQuad
implements AutoCloseable {
    private static final int VERTEX_COUNT = 4;
    private final int vao;
    private final int vbo;

    private FullscreenQuad(int vao, int vbo) {
        this.vao = vao;
        this.vbo = vbo;
    }

    public static FullscreenQuad create() {
        float[] vertices = new float[]{-1.0f, -1.0f, 0.0f, 0.0f, 0.0f, 1.0f, -1.0f, 0.0f, 1.0f, 0.0f, -1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 0.0f, 1.0f, 1.0f};
        FloatBuffer buffer = BufferUtils.createFloatBuffer((int)vertices.length);
        buffer.put(vertices);
        buffer.flip();
        int vao = GL30.glGenVertexArrays();
        int vbo = GL15.glGenBuffers();
        GL30.glBindVertexArray((int)vao);
        GL15.glBindBuffer((int)34962, (int)vbo);
        GL15.glBufferData((int)34962, (FloatBuffer)buffer, (int)35044);
        int stride = 20;
        GL20.glEnableVertexAttribArray((int)0);
        GL20.glVertexAttribPointer((int)0, (int)3, (int)5126, (boolean)false, (int)stride, (long)0L);
        GL20.glEnableVertexAttribArray((int)1);
        GL20.glVertexAttribPointer((int)1, (int)2, (int)5126, (boolean)false, (int)stride, (long)12L);
        GL30.glBindVertexArray((int)0);
        GL15.glBindBuffer((int)34962, (int)0);
        return new FullscreenQuad(vao, vbo);
    }

    public void bind() {
        GL30.glBindVertexArray((int)this.vao);
    }

    public void draw() {
        GL11.glDrawArrays((int)5, (int)0, (int)4);
    }

    public static void unbind() {
        GL30.glBindVertexArray((int)0);
    }

    @Override
    public void close() {
        GL30.glBindVertexArray((int)0);
        GL15.glBindBuffer((int)34962, (int)0);
        GL15.glDeleteBuffers((int)this.vbo);
        GL30.glDeleteVertexArrays((int)this.vao);
    }
}

