#version 150

uniform sampler2D MainSampler;
uniform sampler2D BlurSampler;
uniform float GlowIntensity;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 main = texture(MainSampler, texCoord);
    vec4 blur = texture(BlurSampler, texCoord);

    // Additive blending for glow effect
    fragColor = main + blur * GlowIntensity;
}
