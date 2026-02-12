package dev.hunchclient.render;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

/**
 * Custom Glow shader using raw OpenGL for smooth, shiny ESP effects
 * Based on GaussianBlurShader pattern
 */
public class GlowShader {

    private int programId;
    private int vertexShaderId;
    private int fragmentShaderId;

    private int uTime;
    private int uGlowIntensity;
    private int uModelViewMat;
    private int uProjMat;

    private static final String VERTEX_SHADER = """
        #version 330 core

        layout(location = 0) in vec3 position;
        layout(location = 1) in vec4 color;

        uniform mat4 uModelViewMat;
        uniform mat4 uProjMat;

        out vec4 vertexColor;
        out vec3 worldPos;

        void main() {
            vec4 worldPosition = uModelViewMat * vec4(position, 1.0);
            gl_Position = uProjMat * worldPosition;

            vertexColor = color;
            worldPos = position;
        }
        """;

    private static final String FRAGMENT_SHADER = """
        #version 330 core

        in vec4 vertexColor;
        in vec3 worldPos;

        uniform float uTime;
        uniform float uGlowIntensity;

        out vec4 fragColor;

        void main() {
            // Animated pulsing glow
            float pulse = sin(uTime * 2.0) * 0.2 + 0.9;

            // Distance-based glow falloff from box edges
            vec3 boxCenter = fract(worldPos);
            vec3 distFromCenter = abs(boxCenter - vec3(0.5));
            float maxDist = max(max(distFromCenter.x, distFromCenter.y), distFromCenter.z);

            // Smooth glow falloff - very soft for bloom effect
            float glow = 1.0 - smoothstep(0.0, 0.5, maxDist);
            glow = pow(glow, 0.4); // Soft falloff

            // Brighten colors and add glow
            vec3 glowColor = vertexColor.rgb * (1.0 + glow * pulse * uGlowIntensity * 3.0);
            float glowAlpha = vertexColor.a * (0.4 + glow * pulse * 0.6);

            fragColor = vec4(glowColor, glowAlpha);
        }
        """;

    public GlowShader() {
        compile();
    }

    private void compile() {
        try {
            // Create program
            programId = GL20.glCreateProgram();

            // Compile vertex shader
            vertexShaderId = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
            GL20.glShaderSource(vertexShaderId, VERTEX_SHADER);
            GL20.glCompileShader(vertexShaderId);

            if (GL20.glGetShaderi(vertexShaderId, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
                System.err.println("[GlowShader] Vertex shader compilation failed:");
                System.err.println(GL20.glGetShaderInfoLog(vertexShaderId));
                return;
            }

            // Compile fragment shader
            fragmentShaderId = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
            GL20.glShaderSource(fragmentShaderId, FRAGMENT_SHADER);
            GL20.glCompileShader(fragmentShaderId);

            if (GL20.glGetShaderi(fragmentShaderId, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
                System.err.println("[GlowShader] Fragment shader compilation failed:");
                System.err.println(GL20.glGetShaderInfoLog(fragmentShaderId));
                return;
            }

            // Link program
            GL20.glAttachShader(programId, vertexShaderId);
            GL20.glAttachShader(programId, fragmentShaderId);
            GL20.glLinkProgram(programId);

            if (GL20.glGetProgrami(programId, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                System.err.println("[GlowShader] Program linking failed:");
                System.err.println(GL20.glGetProgramInfoLog(programId));
                return;
            }

            // Get uniform locations
            uTime = GL20.glGetUniformLocation(programId, "uTime");
            uGlowIntensity = GL20.glGetUniformLocation(programId, "uGlowIntensity");
            uModelViewMat = GL20.glGetUniformLocation(programId, "uModelViewMat");
            uProjMat = GL20.glGetUniformLocation(programId, "uProjMat");

            System.out.println("[GlowShader] Custom glow shader compiled successfully!");
        } catch (Exception e) {
            System.err.println("[GlowShader] Failed to compile shader: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void use() {
        GL20.glUseProgram(programId);
    }

    public void setTime(float time) {
        GL20.glUniform1f(uTime, time);
    }

    public void setGlowIntensity(float intensity) {
        GL20.glUniform1f(uGlowIntensity, intensity);
    }

    public void setModelViewMat(org.joml.Matrix4f matrix) {
        float[] matArray = new float[16];
        matrix.get(matArray);
        GL20.glUniformMatrix4fv(uModelViewMat, false, matArray);
    }

    public void setProjMat(org.joml.Matrix4f matrix) {
        float[] matArray = new float[16];
        matrix.get(matArray);
        GL20.glUniformMatrix4fv(uProjMat, false, matArray);
    }

    public void unbind() {
        GL20.glUseProgram(0);
    }

    public boolean isValid() {
        return programId != 0;
    }

    public void cleanup() {
        if (vertexShaderId != 0) GL20.glDeleteShader(vertexShaderId);
        if (fragmentShaderId != 0) GL20.glDeleteShader(fragmentShaderId);
        if (programId != 0) GL20.glDeleteProgram(programId);
    }
}
