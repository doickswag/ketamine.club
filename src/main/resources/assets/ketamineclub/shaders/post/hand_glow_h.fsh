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

layout(std140) uniform GlowConfig {
    int GlowRadius;
};

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 texel = 1.0 / ScreenSize;

    float invSpan = 1.0 / float(GlowRadius + 1);

    float acc = 0.0;
    float wSum = 0.0;
    float maxA = 0.0;
    vec3 col = vec3(0.0);
    for (int x = -GlowRadius; x <= GlowRadius; x++) {
        vec4 s = texture(InSampler, texCoord + texel * vec2(float(x), 0.0));
        float t = 1.0 - float(x) * invSpan * float(x) * invSpan;
        float w = t * t;
        acc  += w * s.a;
        wSum += w;
        if (s.a > maxA) {
            maxA = s.a;
            col = s.rgb;
        }
    }

    fragColor = vec4(col, acc / wSum);
}
