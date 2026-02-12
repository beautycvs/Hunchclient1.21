#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 OutSize;
uniform float Radius;
uniform vec2 Direction;

in vec2 texCoord;
out vec4 fragColor;

// Gaussian weight calculation
float gaussian(float x, float sigma) {
    return exp(-(x * x) / (2.0 * sigma * sigma)) / (sigma * sqrt(2.0 * 3.14159265359));
}

void main() {
    vec2 texelSize = 1.0 / OutSize;
    float sigma = Radius / 2.0;

    vec4 color = vec4(0.0);
    float totalWeight = 0.0;

    // Sample along direction (horizontal or vertical)
    int samples = int(ceil(Radius * 2.0));
    for (int i = -samples; i <= samples; i++) {
        float offset = float(i);
        float weight = gaussian(offset, sigma);

        vec2 sampleCoord = texCoord + Direction * texelSize * offset;
        color += texture(DiffuseSampler, sampleCoord) * weight;
        totalWeight += weight;
    }

    // Normalize
    fragColor = color / totalWeight;
}
