#version 330

in vec3 Position;
in vec4 Color;

out vec2 ndc;

out vec4 vColor;

void main() {
    ndc = Position.xy;
    vColor = Color;
    gl_Position = vec4(Position.xy, 1.0, 1.0);
}
