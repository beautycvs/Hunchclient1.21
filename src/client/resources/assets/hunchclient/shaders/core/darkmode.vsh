#version 150

// Fullscreen quad vertex shader for DarkMode
// Input: Simple 2D position
in vec2 Position;

// Output: Texture coordinates for fragment shader
out vec2 texCoord;

void main() {
    // Convert from clip space [-1, 1] to texture space [0, 1]
    texCoord = Position * 0.5 + 0.5;

    // Pass position directly to clip space (fullscreen quad)
    gl_Position = vec4(Position, 0.0, 1.0);
}
