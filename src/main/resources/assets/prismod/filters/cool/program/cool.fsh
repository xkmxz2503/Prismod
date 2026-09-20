#version 150
uniform sampler2D DiffuseSampler;
uniform float Intensity;
uniform vec2 ScreenSize;
in vec2 texCoord;
out vec4 fragColor;
vec3 filterColor(vec3 color) { vec3 tinted = color * vec3(0.88, 1.02, 1.14); float originalLuma = dot(color, vec3(0.2126, 0.7152, 0.0722)); float tintedLuma = dot(tinted, vec3(0.2126, 0.7152, 0.0722)); return clamp(tinted * (originalLuma / max(tintedLuma, 0.0001)), 0.0, 1.0); }
void main() { vec2 halfPixel = 0.5 / max(ScreenSize, vec2(1.0)); vec4 source = texture(DiffuseSampler, clamp(texCoord, halfPixel, vec2(1.0) - halfPixel)); fragColor = vec4(mix(source.rgb, filterColor(source.rgb), clamp(Intensity, 0.0, 1.0)), source.a); }
