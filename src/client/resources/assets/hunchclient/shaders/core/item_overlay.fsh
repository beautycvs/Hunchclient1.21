#version 150

uniform sampler2D Sampler0;        // Item texture (for alpha mask)
uniform sampler2D Sampler2;        // Light map
uniform sampler2D Sampler1;        // Overlay texture (user's PNG)

uniform float GameTime;
uniform vec2 UVOffset;             // Parallax offset for overlay
uniform float OverlayRotation;     // Rotation in radians
uniform int OverlayBlendMode;      // 0=Replace, 1=Multiply, 2=Add

in vec4 vertexColor;
in vec2 texCoord0;
in vec2 texCoord2;
in vec3 normal;
in vec3 viewSpacePos;

out vec4 fragColor;

// Rotate UV coordinates around center
vec2 rotateUV(vec2 uv, float angle) {
    vec2 center = vec2(0.5);
    mat2 rotation = mat2(
        cos(angle), -sin(angle),
        sin(angle), cos(angle)
    );
    return rotation * (uv - center) + center;
}

void main() {
    // Sample item texture for ALPHA MASK ONLY
    vec4 itemColor = texture(Sampler0, texCoord0);

    // Discard if item is transparent here
    if (itemColor.a < 0.01) {
        discard;
    }

    // DEBUG TEST: Output solid RED to verify shader is running
    // If you see RED, the shader works. If nothing, the RenderPipeline is broken.
    fragColor = vec4(1.0, 0.0, 0.0, 0.8);  // Bright red, slightly transparent

    /* ORIGINAL CODE - DISABLED FOR TESTING
    // Calculate overlay UV from VIEW SPACE POSITION
    vec2 overlayUV;
    overlayUV.x = viewSpacePos.x * 0.5 + 0.5;
    overlayUV.y = viewSpacePos.y * 0.5 + 0.5;

    overlayUV += UVOffset;
    overlayUV.x += sin(GameTime * 1000.0 + overlayUV.y * 3.0) * 0.02;
    overlayUV.y += cos(GameTime * 1000.0 + overlayUV.x * 2.0) * 0.02;

    if (abs(OverlayRotation) > 0.001) {
        overlayUV = rotateUV(overlayUV, OverlayRotation);
    }

    vec4 overlayColor = texture(Sampler1, overlayUV);
    vec4 lightMapColor = texture(Sampler2, texCoord2);

    vec3 finalColor;
    float finalAlpha;

    if (OverlayBlendMode == 0) {
        finalColor = overlayColor.rgb;
        finalAlpha = overlayColor.a * itemColor.a;
    } else if (OverlayBlendMode == 1) {
        finalColor = overlayColor.rgb * itemColor.rgb;
        finalAlpha = overlayColor.a * itemColor.a;
    } else {
        finalColor = overlayColor.rgb;
        finalAlpha = overlayColor.a * itemColor.a * 0.8;
    }

    finalColor *= lightMapColor.rgb;
    fragColor = vec4(finalColor, finalAlpha);
    */
}
