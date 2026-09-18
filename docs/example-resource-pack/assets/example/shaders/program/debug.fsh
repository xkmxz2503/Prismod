#version 150
uniform sampler2D DiffuseSampler;
uniform float Intensity;
in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 source = texture(DiffuseSampler, texCoord);
    vec3 inverted = vec3(1.0) - source.rgb;
    fragColor = vec4(mix(source.rgb, inverted, clamp(Intensity, 0.0, 1.0)), source.a);
}
