#version 150

uniform sampler2D DiffuseSampler;

in vec2 uv;
out vec4 fragColor;

void main() {
    fragColor = texture(DiffuseSampler, uv);
}
