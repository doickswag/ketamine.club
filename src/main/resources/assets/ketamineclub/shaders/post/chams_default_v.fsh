#version 330

uniform sampler2D InSampler;
uniform sampler2D ColorSampler;
uniform sampler2D OrigSampler;

layout(std140) uniform Globals {
    ivec3 CameraBlockPos;
    vec3 CameraOffset;
    vec2 ScreenSize;
    float GlintAlpha;
    float GameTime;
    int MenuBlurRadius;
    int UseRgss;
};

layout(std140) uniform ChamsConfig {
    float GlowIntensity;
    float FillTint;
    float FillAlpha;
    int GlowThickness;
    int LineWidth;
    float OutlineAlpha;
    float InnerGlowIntensity;
    int InnerGlowThickness;
};

in vec2 texCoord;
out vec4 fragColor;

vec3 propagatedColor(vec2 texel) {
    int radius = max(LineWidth, GlowThickness);
    vec3 cAcc = vec3(0.0);
    float wAcc = 0.0;
    for (int y = -radius; y <= radius; y++) {
        vec4 s = texture(ColorSampler, texCoord + texel * vec2(0.0, float(y)));
        cAcc += s.rgb;
        wAcc += s.a;
    }
    return wAcc > 0.0001 ? cAcc / wAcc : vec3(1.0);
}

void main() {
    vec2 texel = 1.0 / ScreenSize;
    vec4 center = texture(OrigSampler, texCoord);

    if (center.a > 0.0) {
        float alpha = center.a * FillAlpha;
        vec3 rgb = center.rgb * FillTint;

        if (InnerGlowThickness > 0) {
            float invSpan = 1.0 / float(InnerGlowThickness + 1);
            float acc = 0.0;
            float wSum = 0.0;
            for (int y = -InnerGlowThickness; y <= InnerGlowThickness; y++) {
                float t = 1.0 - float(y) * invSpan * float(y) * invSpan;
                float w = t * t;
                acc  += w * texture(InSampler, texCoord + texel * vec2(0.0, float(y))).b;
                wSum += w;
            }
            float edge = clamp(1.0 - acc / wSum, 0.0, 1.0);
            float glow = clamp(pow(InnerGlowIntensity * edge, 0.72) * 1.35, 0.0, 1.0);
            alpha = max(alpha, glow * center.a);
            rgb = mix(rgb, center.rgb, glow);
        }

        fragColor = vec4(rgb, alpha);
        return;
    }

    vec3 col = propagatedColor(texel);

    if (LineWidth > 0) {
        float maxA = 0.0;
        for (int y = -LineWidth; y <= LineWidth; y++) {
            maxA = max(maxA, texture(InSampler, texCoord + texel * vec2(0.0, float(y))).r);
        }
        if (maxA > 0.0) {
            fragColor = vec4(col, maxA * OutlineAlpha);
            return;
        }
    }

    if (GlowThickness > 0) {
        float invSpan = 1.0 / float(GlowThickness + 1);
        float acc = 0.0;
        float wSum = 0.0;
        for (int y = -GlowThickness; y <= GlowThickness; y++) {
            float t = 1.0 - float(y) * invSpan * float(y) * invSpan;
            float w = t * t;
            acc  += w * texture(InSampler, texCoord + texel * vec2(0.0, float(y))).g;
            wSum += w;
        }
        float coverage = acc / wSum;
        float glow = clamp(pow(GlowIntensity * coverage, 0.72) * 1.35, 0.0, 1.0);
        if (glow > 0.0) {
            fragColor = vec4(col, glow);
            return;
        }
    }

    fragColor = vec4(0.0);
}
