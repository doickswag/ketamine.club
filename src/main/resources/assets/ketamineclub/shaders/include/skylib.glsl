#version 330

#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>

vec3 sky_view_dir(vec2 ndc) {
    mat4 invVP = inverse(ProjMat * ModelViewMat);
    vec4 near = invVP * vec4(ndc, -1.0, 1.0);
    vec4 far  = invVP * vec4(ndc,  1.0, 1.0);
    return normalize(far.xyz / far.w - near.xyz / near.w);
}

float sky_hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float sky_noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(sky_hash(i),               sky_hash(i + vec2(1.0, 0.0)), u.x),
               mix(sky_hash(i + vec2(0.0, 1.0)), sky_hash(i + vec2(1.0, 1.0)), u.x), u.y);
}

float sky_fbm(vec2 p) {
    float value = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 5; i++) {
        value += amp * sky_noise(p);
        p *= 2.02;
        amp *= 0.5;
    }
    return value;
}
