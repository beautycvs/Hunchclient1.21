#version 150

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec4 vertexColor;
out vec2 texCoord0;
out vec2 texCoord2;
out vec3 normal;
out vec3 viewSpacePos;

void main() {
    vec4 viewPos = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * viewPos;

    vertexColor = Color;
    texCoord0 = UV0;
    texCoord2 = vec2(UV2) / 256.0;
    normal = normalize((ModelViewMat * vec4(Normal, 0.0)).xyz);
    viewSpacePos = viewPos.xyz;
}
