#version 150
uniform sampler2D DiffuseSampler;
uniform float Intensity;
uniform vec2 ScreenSize;
in vec2 texCoord;
out vec4 fragColor;
vec3 filterColor(vec3 color) { float luma = dot(color, vec3(0.2126, 0.7152, 0.0722)); float lifted = clamp(sqrt(max(luma, 0.0)) * 1.18 + 0.07, 0.0, 1.0); return clamp(vec3(0.32, 1.0, 0.42) * lifted + color * 0.08, 0.0, 1.0); }
void main() { vec2 halfPixel = 0.5 / max(ScreenSize, vec2(1.0)); vec4 source = texture(DiffuseSampler, clamp(texCoord, halfPixel, vec2(1.0) - halfPixel)); fragColor = vec4(mix(source.rgb, filterColor(source.rgb), clamp(Intensity, 0.0, 1.0)), source.a); }
