#version 150

uniform sampler2D DiffuseSampler;  // Blurred mask
uniform sampler2D OriginalMask;    // Sharp original mask
uniform vec2 OutSize;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    // Read sharp mask and blurred mask
    float sharpMask = texture(OriginalMask, texCoord).r;
    float blurredMask = texture(DiffuseSampler, texCoord).r;

    // Extract outline: everything that's in blur but not in sharp mask
    float outline = max(0.0, blurredMask - sharpMask);

    // Output white outline with alpha
    fragColor = vec4(1.0, 1.0, 1.0, outline);
}
