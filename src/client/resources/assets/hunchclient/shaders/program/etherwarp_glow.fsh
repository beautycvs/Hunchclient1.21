#version 150

uniform sampler2D DiffuseSampler;
uniform float Radius;
uniform vec2 TexelSize;

in vec2 uv;
out vec4 fragColor;

float weight(float x, float sigma) {
    return exp(-(x * x) / (2.0 * sigma * sigma));
}

void main() {
    float sigma = Radius;
    vec3 color = vec3(0.0);
    float norm = 0.0;

    for (int i = -16; i <= 16; i++) {
        float f = float(i);
        float w = weight(f, sigma);
        vec2 offset = vec2(1.0, 0.0) * f * TexelSize;
        color += texture(DiffuseSampler, uv + offset).rgb * w;
        norm += w;
    }

    fragColor = vec4(color / norm, 1.0);
}

