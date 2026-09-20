#version 150
uniform sampler2D DiffuseSampler;
uniform sampler2D LutSampler;
uniform float Intensity;
uniform vec3 LutDomainMin;
uniform vec3 LutDomainMax;
uniform vec2 ScreenSize;
in vec2 texCoord;
out vec4 fragColor;
void main() {
    vec2 halfPixel = 0.5 / max(ScreenSize, vec2(1.0));
    vec4 source = texture(DiffuseSampler, clamp(texCoord, halfPixel, vec2(1.0) - halfPixel));
    vec3 coordinate = clamp((source.rgb - LutDomainMin) / max(LutDomainMax - LutDomainMin, vec3(0.000001)), vec3(0.0), vec3(1.0)) * 31.0;
    float x = (floor(coordinate.x) + floor(coordinate.y) * 32.0 + 0.5) / 1024.0;
    float y = (floor(coordinate.z) + 0.5) / 32.0;
    vec3 lut = texture(LutSampler, vec2(x, y)).rgb;
    fragColor = vec4(mix(source.rgb, lut, clamp(Intensity, 0.0, 1.0)), source.a);
}
