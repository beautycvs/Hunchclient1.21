package dev.hunchclient.render;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.FloatBuffer;

import org.lwjgl.BufferUtils;

/**
 * Custom Gaussian Blur shader using raw OpenGL.
 * NO MINECRAFT WRAPPERS - pure LWJGL/OpenGL implementation.
 */
public class GaussianBlurShader {

    private int programId;
    private int vertexShaderId;
    private int fragmentShaderId;

    private int uTexture;
    private int uDirection;
    private int uRadius;
    private int uResolution;

    private static final String VERTEX_SHADER = """
        #version 330 core

        layout(location = 0) in vec2 position;
        layout(location = 1) in vec2 texCoord;

        out vec2 vTexCoord;

        void main() {
            vTexCoord = texCoord;
            gl_Position = vec4(position, 0.0, 1.0);
        }
        """;

    private static final String FRAGMENT_SHADER = """
        #version 330 core

        in vec2 vTexCoord;
        out vec4 fragColor;

        uniform sampler2D uTexture;
        uniform vec2 uDirection;      // (1,0) for horizontal, (0,1) for vertical
        uniform float uRadius;
        uniform vec2 uResolution;

        // Gaussian weight function
        float gaussian(float x, float sigma) {
            float sigma2 = sigma * sigma;
            return exp(-(x * x) / (2.0 * sigma2)) / (sigma * sqrt(2.0 * 3.14159265359));
        }

        void main() {
            vec2 texelSize = 1.0 / uResolution;
            float sigma = uRadius / 2.0;

            vec4 color = vec4(0.0);
            float totalWeight = 0.0;

            int samples = int(ceil(uRadius * 2.0));
            for (int i = -samples; i <= samples; i++) {
                float offset = float(i);
                float weight = gaussian(offset, sigma);

                vec2 sampleCoord = vTexCoord + uDirection * texelSize * offset;
                color += texture(uTexture, sampleCoord) * weight;
                totalWeight += weight;
            }

            fragColor = color / totalWeight;
        }
        """;

    public GaussianBlurShader() {
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
            System.err.println("[GaussianBlur] Vertex shader compilation failed:");
            System.err.println(GL20.glGetShaderInfoLog(vertexShaderId));
        }

        // Compile fragment shader
        fragmentShaderId = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
        GL20.glShaderSource(fragmentShaderId, FRAGMENT_SHADER);
        GL20.glCompileShader(fragmentShaderId);

        if (GL20.glGetShaderi(fragmentShaderId, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            System.err.println("[GaussianBlur] Fragment shader compilation failed:");
            System.err.println(GL20.glGetShaderInfoLog(fragmentShaderId));
        }

        // Link program
        GL20.glAttachShader(programId, vertexShaderId);
        GL20.glAttachShader(programId, fragmentShaderId);
        GL20.glLinkProgram(programId);

        if (GL20.glGetProgrami(programId, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            System.err.println("[GaussianBlur] Program linking failed:");
            System.err.println(GL20.glGetProgramInfoLog(programId));
        }

        // Get uniform locations
        uTexture = GL20.glGetUniformLocation(programId, "uTexture");
        uDirection = GL20.glGetUniformLocation(programId, "uDirection");
        uRadius = GL20.glGetUniformLocation(programId, "uRadius");
        uResolution = GL20.glGetUniformLocation(programId, "uResolution");

        System.out.println("[GaussianBlur] Shader compiled successfully!");
    }

    public void use() {
        GL20.glUseProgram(programId);
    }

    public void setTexture(int textureUnit) {
        GL20.glUniform1i(uTexture, textureUnit);
    }

    public void setDirection(float x, float y) {
        GL20.glUniform2f(uDirection, x, y);
    }

    public void setRadius(float radius) {
        GL20.glUniform1f(uRadius, radius);
    }

    public void setResolution(float width, float height) {
        GL20.glUniform2f(uResolution, width, height);
    }

    public void cleanup() {
        GL20.glDeleteShader(vertexShaderId);
        GL20.glDeleteShader(fragmentShaderId);
        GL20.glDeleteProgram(programId);
    }
}
