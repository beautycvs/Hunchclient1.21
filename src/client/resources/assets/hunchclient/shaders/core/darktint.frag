#version 330 core

in vec2 fragTexCoord;

uniform sampler2D uTexture;
uniform vec4 uTintColor;
uniform float uIntensity;
uniform int uBlendMode;
uniform float uVignetteStrength;
uniform float uSaturation;
uniform float uContrast;
uniform float uChromaticAberration;
uniform float uBrightness;

out vec4 fragColor;

// Convert RGB to Luminance
float getLuminance(vec3 color) {
    return dot(color, vec3(0.299, 0.587, 0.114));
}

// Apply saturation adjustment
vec3 applySaturation(vec3 color, float sat) {
    float lum = getLuminance(color);
    return mix(vec3(lum), color, sat);
}

// Apply contrast adjustment
vec3 applyContrast(vec3 color, float contrast) {
    return (color - 0.5) * contrast + 0.5;
}

// Vignette effect
float getVignette(vec2 uv, float strength) {
    vec2 center = uv - 0.5;
    float dist = length(center);
    return 1.0 - smoothstep(0.3, 1.0, dist) * strength;
}

void main() {
    vec2 uv = fragTexCoord;

    // Chromatic Aberration (color shift at edges)
    // Fixed to use distance-based scaling to prevent vertical lines
    vec3 worldColor;
    if (uChromaticAberration > 0.001) {
        // Calculate distance from center for smoother aberration
        vec2 dir = (uv - 0.5);
        float dist = length(dir);

        // Normalize direction and scale by distance for radial effect
        vec2 offset = normalize(dir) * dist * uChromaticAberration;

        // Sample with smooth offset to prevent banding
        worldColor.r = texture(uTexture, uv + offset * 0.5).r;
        worldColor.g = texture(uTexture, uv).g;
        worldColor.b = texture(uTexture, uv - offset * 0.5).b;
    } else {
        worldColor = texture(uTexture, uv).rgb;
    }

    // Apply saturation
    worldColor = applySaturation(worldColor, uSaturation);

    // Apply contrast
    worldColor = applyContrast(worldColor, uContrast);

    // Apply tint based on blend mode
    vec3 result;

    if (uBlendMode == 0) {
        // Multiply blend - darkens based on tint color
        vec3 tint = mix(vec3(1.0), uTintColor.rgb, uIntensity);
        result = worldColor * tint;
    } else if (uBlendMode == 1) {
        // Overlay blend - preserves highlights and shadows
        vec3 tint = uTintColor.rgb;
        vec3 base = worldColor;
        vec3 overlayed;

        for (int i = 0; i < 3; i++) {
            if (base[i] < 0.5) {
                overlayed[i] = 2.0 * base[i] * tint[i];
            } else {
                overlayed[i] = 1.0 - 2.0 * (1.0 - base[i]) * (1.0 - tint[i]);
            }
        }

        result = mix(base, overlayed, uIntensity);
    } else if (uBlendMode == 2) {
        // Additive blend - brightens with tint color
        result = worldColor + uTintColor.rgb * uIntensity;
    } else {
        // Screen blend - lightens based on tint
        vec3 inverted = vec3(1.0) - (vec3(1.0) - worldColor) * (vec3(1.0) - uTintColor.rgb);
        result = mix(worldColor, inverted, uIntensity);
    }

    // Apply vignette
    float vignette = getVignette(uv, uVignetteStrength);
    result *= vignette;

    // Apply brightness multiplier (to compensate for darkening from tint)
    result *= uBrightness;

    fragColor = vec4(result, 1.0);
}
