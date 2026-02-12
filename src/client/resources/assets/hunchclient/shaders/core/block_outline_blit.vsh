#version 150

in vec3 Position;

uniform mat4 ProjMat;

out vec2 texCoord;

void main() {
    gl_Position = ProjMat * vec4(Position, 1.0);

    // Position is in [-1, 1], convert to [0, 1] for texCoord
    texCoord = Position.xy * 0.5 + 0.5;
}
