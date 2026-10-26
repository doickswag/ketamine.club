#version 330

#moj_import <ketamineclub:skylib.glsl>

in vec2 ndc;
out vec4 fragColor;

void main() {
    vec3 dir = sky_view_dir(ndc);
    float t = GameTime * 600.0;

    float up = clamp(dir.y, 0.0, 1.0);
    vec3 horizon = vec3(1.0, 0.5, 0.2);
    vec3 zenith  = vec3(0.1, 0.2, 0.45);
    vec3 col = mix(horizon, zenith, pow(up, 0.5));

    vec3 sunDir = normalize(vec3(cos(t * 0.05), 0.06, sin(t * 0.05)));
    float sun = max(dot(dir, sunDir), 0.0);
    col += vec3(1.0, 0.75, 0.4) * pow(sun, 8.0) * 0.6;
    col += vec3(1.0, 0.9, 0.7) * smoothstep(0.9975, 0.999, sun);

    float cloud = sky_fbm(vec2(dir.x * 2.0 + t * 0.03, dir.z * 2.0));
    cloud *= smoothstep(0.6, 0.05, dir.y) * smoothstep(0.0, 0.15, dir.y);
    col = mix(col, vec3(1.0, 0.85, 0.75), smoothstep(0.55, 0.85, cloud) * 0.6);

    fragColor = vec4(col, 1.0);
}
