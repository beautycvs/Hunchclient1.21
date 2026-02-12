#version 150

uniform sampler2D MainSampler;
uniform sampler2D BlurSampler;
uniform float GlowIntensity;

in vec2 uv;
out vec4 fragColor;

void main() {
    vec4 main = texture(MainSampler, uv);
    vec4 blur = texture(BlurSampler, uv);

    // Additive blending for glow effect
    // The blur is added on top of the main image for a nice glow
    fragColor = main + blur * GlowIntensity;
}

