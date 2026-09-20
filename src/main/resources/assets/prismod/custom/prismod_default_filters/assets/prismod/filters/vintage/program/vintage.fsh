#version 150
uniform sampler2D DiffuseSampler;
uniform float Intensity;
uniform vec2 ScreenSize;
in vec2 texCoord;
out vec4 fragColor;
vec3 filterColor(vec3 color) { float luma = dot(color, vec3(0.2126, 0.7152, 0.0722)); vec3 faded = mix(color, vec3(luma), 0.25); return clamp((faded - 0.5) * 0.82 + 0.5 + vec3(0.055, 0.02, -0.04), 0.0, 1.0); }
void main() { vec2 halfPixel = 0.5 / max(ScreenSize, vec2(1.0)); vec4 source = texture(DiffuseSampler, clamp(texCoord, halfPixel, vec2(1.0) - halfPixel)); fragColor = vec4(mix(source.rgb, filterColor(source.rgb), clamp(Intensity, 0.0, 1.0)), source.a); }
