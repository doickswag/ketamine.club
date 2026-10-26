#version 330

#moj_import <ketamineclub:skylib.glsl>

in vec2 ndc;
out vec4 fragColor;

void main() {
    vec3 dir = sky_view_dir(ndc);
    float t = GameTime * 400.0;

    vec2 p = dir.xy * 2.0 + dir.z;
    float a = sky_fbm(p * 1.5 + vec2(t * 0.02, 0.0));
    float b = sky_fbm(p * 3.0 - vec2(0.0, t * 0.015));

    vec3 col = vec3(0.02, 0.0, 0.05);
    col += mix(vec3(0.6, 0.1, 0.5), vec3(0.1, 0.4, 0.7), a) * pow(a, 1.5);
    col += vec3(0.2, 0.05, 0.4) * pow(b, 2.5);

    vec2 starUv = dir.xz / max(abs(dir.y) + 0.2, 0.001);
    float star = step(0.997, sky_hash(floor(starUv * 200.0)));
    col += star * vec3(1.0);

    fragColor = vec4(col, 1.0);
}
