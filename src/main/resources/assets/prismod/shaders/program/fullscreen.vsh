#version 150
in vec4 Position;
uniform mat4 ProjMat;
uniform vec2 OutSize;
out vec2 texCoord;
void main() {
    gl_Position = ProjMat * vec4(Position.xy, 0.0, 1.0);
    gl_Position.z = 0.2;
    texCoord = Position.xy / OutSize;
}
