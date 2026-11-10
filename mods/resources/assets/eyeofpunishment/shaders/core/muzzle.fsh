#version 150
uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
in vec4 vertexColor;
in vec2 texCoord0;
out vec4 fragColor;
// Source: 2 * TintColor(.5) * vertexRGBA * textureRGBA; DyeingColor=1.
// Soft alpha is preserved; no cutoff, premultiplication, added gain or lighting.
void main() {
    fragColor = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
}
