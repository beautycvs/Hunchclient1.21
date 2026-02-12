package dev.hunchclient.render;

import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import net.minecraft.client.Minecraft;

/**
 * Simple shader for rendering colored boxes
 * Uses Minecraft's projection and modelview matrices
 */
public class SimpleBoxShader {

    private int programId;
    private int vertexShaderId;
    private int fragmentShaderId;

    private int uProjection;
    private int uModelView;

    private static final String VERTEX_SHADER = """
        #version 330 core

        layout(location = 0) in vec3 position;
        layout(location = 1) in vec4 color;

        out vec4 vColor;

        uniform mat4 uProjection;
        uniform mat4 uModelView;

        void main() {
            vColor = color;
            gl_Position = uProjection * uModelView * vec4(position, 1.0);
        }
        """;

    private static final String FRAGMENT_SHADER = """
        #version 330 core

        in vec4 vColor;
        out vec4 fragColor;

        void main() {
            fragColor = vColor;
        }
        """;

    public SimpleBoxShader() {
        compile();
    }

    private void compile() {
        // Create program
        programId = GL20.glCreateProgram();

        // Compile vertex shader
        vertexShaderId = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
        GL20.glShaderSource(vertexShaderId, VERTEX_SHADER);
        GL20.glCompileShader(vertexShaderId);

        if (GL20.glGetShaderi(vertexShaderId, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            System.err.println("[SimpleBoxShader] Vertex shader compilation failed:");
            System.err.println(GL20.glGetShaderInfoLog(vertexShaderId));
        }

        // Compile fragment shader
        fragmentShaderId = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
        GL20.glShaderSource(fragmentShaderId, FRAGMENT_SHADER);
        GL20.glCompileShader(fragmentShaderId);

        if (GL20.glGetShaderi(fragmentShaderId, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            System.err.println("[SimpleBoxShader] Fragment shader compilation failed:");
            System.err.println(GL20.glGetShaderInfoLog(fragmentShaderId));
        }

        // Link program
        GL20.glAttachShader(programId, vertexShaderId);
        GL20.glAttachShader(programId, fragmentShaderId);
        GL20.glLinkProgram(programId);

        if (GL20.glGetProgrami(programId, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            System.err.println("[SimpleBoxShader] Program linking failed:");
            System.err.println(GL20.glGetProgramInfoLog(programId));
        }

        // Get uniform locations
        uProjection = GL20.glGetUniformLocation(programId, "uProjection");
        uModelView = GL20.glGetUniformLocation(programId, "uModelView");

        System.out.println("[SimpleBoxShader] Shader compiled successfully!");
    }

    public void use() {
        GL20.glUseProgram(programId);
    }

    public void setMatrices() {
        // Get modelview matrix from RenderSystem
        Matrix4f modelView = RenderSystem.getModelViewMatrix();

        // Get projection matrix - use a simple perspective matrix
        Minecraft mc = Minecraft.getInstance();
        float fov = 70.0f; // Default FOV
        float aspect = (float) mc.getWindow().getWidth() / (float) mc.getWindow().getHeight();
        float nearPlane = 0.05f;
        float farPlane = 1000.0f;

        Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(fov), aspect, nearPlane, farPlane);

        // Upload to shader
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer projBuffer = stack.mallocFloat(16);
            projection.get(projBuffer);
            GL20.glUniformMatrix4fv(uProjection, false, projBuffer);

            FloatBuffer mvBuffer = stack.mallocFloat(16);
            modelView.get(mvBuffer);
            GL20.glUniformMatrix4fv(uModelView, false, mvBuffer);
        }
    }

    public void cleanup() {
        GL20.glDeleteShader(vertexShaderId);
        GL20.glDeleteShader(fragmentShaderId);
        GL20.glDeleteProgram(programId);
    }
}
