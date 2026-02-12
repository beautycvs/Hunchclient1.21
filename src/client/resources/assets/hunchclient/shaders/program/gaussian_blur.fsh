#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 Direction;
uniform vec2 TexelSize;

in vec2 texCoord;
out vec4 fragColor;

// Gaussian weights for 9-tap blur
const float weights[5] = float[](0.227027, 0.1945946, 0.1216216, 0.054054, 0.016216);

void main() {
    vec4 result = texture(DiffuseSampler, texCoord) * weights[0];

    for(int i = 1; i < 5; i++) {
        vec2 offset = Direction * TexelSize * float(i);
        result += texture(DiffuseSampler, texCoord + offset) * weights[i];
        result += texture(DiffuseSampler, texCoord - offset) * weights[i];
    }

    fragColor = result;
}
