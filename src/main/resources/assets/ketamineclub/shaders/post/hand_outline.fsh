#version 330

uniform sampler2D InSampler;
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

layout(std140) uniform OutlineConfig {
    float FillAlpha;
    float OutlineAlpha;
    int LineWidth;
};

layout(std140) uniform HandColor {
    vec4 Color;
};

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 orig = texture(OrigSampler, texCoord);
    if (orig.a > 0.0) {

        fragColor = FillAlpha > 0.0 ? vec4(Color.rgb, FillAlpha) : vec4(0.0);
        return;
    }

    vec2 texel = 1.0 / ScreenSize;
    float maxA = 0.0;
    for (int y = -LineWidth; y <= LineWidth; y++) {
        vec4 s = texture(InSampler, texCoord + texel * vec2(0.0, float(y)));
        maxA = max(maxA, s.a);
    }

    fragColor = maxA > 0.0 ? vec4(Color.rgb, OutlineAlpha) : vec4(0.0);
}
