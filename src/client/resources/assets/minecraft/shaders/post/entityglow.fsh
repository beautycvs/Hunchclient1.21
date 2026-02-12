#version 330

// radius of the glow
#define Radius 3

// the final alpha of the area covering the entity
#define FinalAlpha 0.1


layout(std140) uniform SamplerInfo {
  vec2 OutSize;
  vec2 InSize;
};

layout(std140) uniform BlurConfig {
  vec2 BlurDir;
};

uniform sampler2D InSampler;
uniform sampler2D Sampler2;

in vec2 texCoord;

out vec4 fragColor;

vec2 sampleStep = 1 / InSize * BlurDir;
float gradient = 1.0 / (Radius + 1.0);
bool isFinal = sampleStep.y != 0;
float finalAlpha = isFinal ? FinalAlpha : 1.0;
float additionalAlpha = isFinal ? gradient : 0.0;

void main() {
  // get the current pixel
  vec4 sampler = texture(InSampler, texCoord);
  if (sampler.a >= 1.0) {
    // if the pixel is opaque then
    fragColor = vec4(sampler.rgb, finalAlpha);
  } else {
    // accumulated RGB colors
    vec3 blurs;
    // number of pixels accumulated in blurs variable
    float totalColor;
    // the alpha value that should be the output
    float alpha = sampler.a;

    if (alpha > 0.0) {
      totalColor = 1;
      blurs = sampler.rgb;
    } else {
      totalColor = 0;
      blurs = vec3(0.0);
    }

    // iterate from far to close
    for (float r = Radius; r > 0; r--) {
      vec2 step = sampleStep * r;
      vec4 sampleL = texture(InSampler, texCoord - step);
      vec4 sampleR = texture(InSampler, texCoord + step);
      if (sampleL.a > 0.0) {
        totalColor++;
        blurs += sampleL.rgb;
        alpha = max(alpha, sampleL.a - r * gradient);
      }
      if (sampleR.a > 0.0) {
        totalColor++;
        blurs += sampleR.rgb;
        alpha = max(alpha, sampleR.a - r * gradient);
      }
    }

    fragColor = alpha > 0.0 ? vec4(blurs / totalColor, alpha + additionalAlpha) : vec4(0.0);
  }
}
