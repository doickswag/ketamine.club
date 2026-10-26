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

layout(std140) uniform DilateConfig {
    int LineWidth;
    int GlowThickness;
    int InnerGlowThickness;
};

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 texel = 1.0 / ScreenSize;
    int radius = max(max(LineWidth, GlowThickness), InnerGlowThickness);

    float invSpan = 1.0 / float(GlowThickness + 1);
    float invInnerSpan = 1.0 / float(InnerGlowThickness + 1);

    float maxA = 0.0;
    float gAcc = 0.0;
    float gW   = 0.0;
    float iAcc = 0.0;
    float iW   = 0.0;
    for (int x = -radius; x <= radius; x++) {
        float a = texture(InSampler, texCoord + texel * vec2(float(x), 0.0)).a;
        if (abs(x) <= LineWidth) maxA = max(maxA, a);
        if (abs(x) <= GlowThickness) {
            float t = 1.0 - float(x) * invSpan * float(x) * invSpan;
            float w = t * t;
            gAcc += w * a;
            gW   += w;
        }
        if (abs(x) <= InnerGlowThickness) {
            float t = 1.0 - float(x) * invInnerSpan * float(x) * invInnerSpan;
            float w = t * t;
            iAcc += w * a;
            iW   += w;
        }
    }

    fragColor = vec4(maxA, gAcc / gW, iAcc / iW, 1.0);
}
