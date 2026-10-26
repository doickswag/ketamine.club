#version 330

#moj_import <ketamineclub:skylib.glsl>

in vec2 ndc;
in vec4 vColor;
out vec4 fragColor;

void main() {
    vec3 dir = sky_view_dir(ndc);
    float t = GameTime * 1000.0;

    float up = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 col = mix(vec3(0.03, 0.04, 0.09), vec3(0.0, 0.0, 0.02), up);

    vec2 starUv = dir.xz / max(abs(dir.y) + 0.15, 0.001);
    float star = step(0.995, sky_hash(floor(starUv * 140.0)));
    col += star * smoothstep(0.05, 0.5, dir.y) * vec3(0.9, 0.95, 1.0);

    vec3 auroraColor = vColor.rgb;
    float horizonMask = smoothstep(0.0, 0.55, dir.y);
    float band = sky_fbm(vec2(dir.x * 3.0 + t * 0.05, dir.y * 7.0 - t * 0.02));
    float aurora = smoothstep(0.45, 0.95, band) * horizonMask;
    col += aurora * mix(auroraColor * 0.35, auroraColor, horizonMask);
    col += aurora * aurora * mix(auroraColor, vec3(1.0), 0.5);

    fragColor = vec4(col, 1.0);
}
