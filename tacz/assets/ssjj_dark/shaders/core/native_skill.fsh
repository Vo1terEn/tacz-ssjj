#version 150
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform vec4 MainST;
uniform vec4 MaskST;
uniform vec4 NoiseST;
uniform vec2 Scroll;
uniform vec2 NoiseScroll;
uniform vec2 Tiles;
uniform vec2 Cell;
uniform float Clock;
uniform float Brightness;
uniform float Alpha;
uniform float Contrast;
uniform float Warp;
uniform float Masked;
in vec2 texUV;
in vec4 vertexColor;
out vec4 fragColor;
vec4 sourceSample(sampler2D s,vec2 uv){return texture(s,vec2(uv.x,1-uv.y));}
void main(){
 vec2 uv=texUV;
 if(Masked>.5){vec2 n=sourceSample(Sampler2,uv*NoiseST.xy+NoiseST.zw+NoiseScroll*Clock).rg;uv+=(n-.5)*Warp;}
 vec2 mainUV=(uv+Cell)/Tiles;mainUV=mainUV*MainST.xy+MainST.zw+Scroll*Clock;
 vec4 c=sourceSample(Sampler0,mainUV);float mask=1;
 if(Masked>.5)mask=sourceSample(Sampler1,texUV*MaskST.xy+MaskST.zw).r;
 c.rgb=pow(max(c.rgb,vec3(0)),vec3(Contrast));
 fragColor=vec4(c.rgb*vertexColor.rgb*Brightness,c.a*vertexColor.a*Alpha*mask);
}
