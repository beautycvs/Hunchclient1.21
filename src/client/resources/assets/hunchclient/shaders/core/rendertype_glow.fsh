#version 150

in vec4 vertexColor;
in vec3 worldPos;

uniform float GameTime;

out vec4 fragColor;

void main() {
    // Animated pulsing glow
    float time = GameTime * 2000.0;
    float pulse = sin(time) * 0.2 + 0.9;

    // Distance-based glow falloff from box edges
    vec3 boxCenter = fract(worldPos);
    vec3 distFromCenter = abs(boxCenter - vec3(0.5));
    float maxDist = max(max(distFromCenter.x, distFromCenter.y), distFromCenter.z);

    // Smooth glow falloff
    float glow = 1.0 - smoothstep(0.0, 0.5, maxDist);
    glow = pow(glow, 0.3); // Very soft falloff for bloom effect

    // Brighten and add glow
    vec3 glowColor = vertexColor.rgb * (1.0 + glow * pulse * 2.0);
    float glowAlpha = vertexColor.a * (0.5 + glow * pulse * 0.5);

    fragColor = vec4(glowColor, glowAlpha);
}
