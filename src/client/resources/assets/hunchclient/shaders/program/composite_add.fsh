#version 150

uniform sampler2D SceneSampler;
uniform sampler2D GlowSampler;
uniform vec3 GlowColor;
uniform float GlowStrength;

in vec2 uv;
out vec4 fragColor;

void main() {
    vec3 scene = texture(SceneSampler, uv).rgb;
    float mask = texture(GlowSampler, uv).r;
    vec3 glow = GlowColor * mask * GlowStrength;
    fragColor = vec4(scene + glow, 1.0);
}
