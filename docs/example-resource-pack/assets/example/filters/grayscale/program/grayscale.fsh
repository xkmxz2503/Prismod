#version 150
uniform sampler2D DiffuseSampler;
uniform float Intensity;
in vec2 texCoord;
out vec4 fragColor;
void main() { vec4 source = texture(DiffuseSampler, texCoord); float gray = dot(source.rgb, vec3(0.2126, 0.7152, 0.0722)); fragColor = vec4(mix(source.rgb, vec3(gray), clamp(Intensity, 0.0, 1.0)), source.a); }
