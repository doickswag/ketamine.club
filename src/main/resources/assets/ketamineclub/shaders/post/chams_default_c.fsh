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

layout(std140) uniform ColorConfig {
    int LineWidth;
    int GlowThickness;
};

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 texel = 1.0 / ScreenSize;

    int radius = max(LineWidth, GlowThickness);
    float norm = 1.0 / float(2 * radius + 1);

    vec3 cAcc = vec3(0.0);
    float wAcc = 0.0;
    for (int x = -radius; x <= radius; x++) {
        vec4 s = texture(InSampler, texCoord + texel * vec2(float(x), 0.0));
        cAcc += s.a * s.rgb;
        wAcc += s.a;
    }

    fragColor = vec4(cAcc * norm, wAcc * norm);
}
