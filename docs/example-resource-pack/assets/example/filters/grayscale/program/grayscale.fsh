#version 150
uniform sampler2D DiffuseSampler;
uniform float Intensity;
uniform float Exposure, Contrast, Highlights, Shadows, Saturation, Temperature, Tint, Gamma;
in vec2 texCoord;
out vec4 fragColor;
vec3 pre(vec3 c) { c *= exp2(Exposure); c *= vec3(1.0 + Temperature * 0.1, 1.0, 1.0 - Temperature * 0.1); c += vec3(Tint * 0.05, -Tint * 0.02, Tint * 0.05); return pow(max(c, vec3(0.0)), vec3(1.0 / max(Gamma, 0.1))); }
vec3 post(vec3 c) { c = (c - 0.5) * (1.0 + Contrast) + 0.5; c += vec3(Shadows * 0.15 + Highlights * 0.1); float l = dot(c, vec3(0.2126, 0.7152, 0.0722)); return mix(vec3(l), c, Saturation); }
void main() { vec4 source = texture(DiffuseSampler, texCoord); vec3 original = pre(source.rgb); float gray = dot(original, vec3(0.2126, 0.7152, 0.0722)); vec3 filtered = post(vec3(gray)); fragColor = vec4(mix(original, filtered, clamp(Intensity, 0.0, 1.0)), source.a); }
