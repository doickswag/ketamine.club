#version 330

#moj_import <ketamineclub:skylib.glsl>

in vec2 ndc;
out vec4 fragColor;

vec3 hue(float h) {
    return clamp(abs(mod(h * 6.0 + vec3(0.0, 4.0, 2.0), 6.0) - 3.0) - 1.0, 0.0, 1.0);
}

void main() {
    vec3 dir = sky_view_dir(ndc);
    float t = GameTime * 1200.0;

    float angle = atan(dir.z, dir.x);
    float warp = sky_fbm(dir.xy * 4.0 + t * 0.05);
    float h = fract(angle / 6.2831853 + dir.y * 0.5 + warp * 0.4 + t * 0.02);

    vec3 col = hue(h);
    col = mix(col, vec3(1.0), pow(warp, 3.0) * 0.5);
    col *= 0.6 + 0.4 * sin(dir.y * 10.0 + t * 0.1);

    fragColor = vec4(col, 1.0);
}
