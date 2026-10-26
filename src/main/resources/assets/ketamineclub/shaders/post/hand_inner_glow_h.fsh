#version 330

uniform sampler2D InSampler;

layout(std140) uniform Globals {
    ivec3 CameraBlockPos;
    vec3 CameraOffset;
    vec2 ScreenSize;
    float GlintAlpha;
    float GameTime;
    int MenuBlurRadius;
    int UseRgss;
};

layout(std140) uniform InnerGlowConfig {
    int InnerGlowRadius;
};

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 texel = 1.0 / ScreenSize;

    float invSpan = 1.0 / float(InnerGlowRadius + 1);

    float acc = 0.0;
    float wSum = 0.0;
    for (int x = -InnerGlowRadius; x <= InnerGlowRadius; x++) {
        float a = texture(InSampler, texCoord + texel * vec2(float(x), 0.0)).a;
        float t = 1.0 - float(x) * invSpan * float(x) * invSpan;
        float w = t * t;
        acc  += w * a;
        wSum += w;
    }

    fragColor = vec4(0.0, 0.0, 0.0, acc / wSum);
}
