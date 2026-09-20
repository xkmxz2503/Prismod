#version 150
uniform sampler2D DiffuseSampler;
uniform float Intensity;
uniform vec2 ScreenSize;
in vec2 texCoord;
out vec4 fragColor;
vec3 filterColor(vec3 color) { return vec3(dot(color, vec3(0.2126, 0.7152, 0.0722))); }
void main() { vec2 halfPixel = 0.5 / max(ScreenSize, vec2(1.0)); vec4 source = texture(DiffuseSampler, clamp(texCoord, halfPixel, vec2(1.0) - halfPixel)); fragColor = vec4(mix(source.rgb, filterColor(source.rgb), clamp(Intensity, 0.0, 1.0)), source.a); }
