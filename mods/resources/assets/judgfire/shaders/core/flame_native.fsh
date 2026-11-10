#version 150
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform float EffectTime;
uniform vec4 MainST;
uniform vec4 MaskST;
uniform vec4 NoiseST;
uniform vec4 Scroll;
uniform vec4 Tint;
uniform vec4 Params;
uniform vec2 Force;
uniform float HeatTime;
uniform float FlowOffset;
uniform vec4 Custom;
uniform vec2 WrapMain;
uniform vec2 WrapMask;
uniform vec2 WrapNoise;
in vec4 vertexColor;
in vec2 texCoord;
out vec4 fragColor;
// Unity Repeat0 / Clamp1 / Mirror2 / MirrorOnce3, per axis. Non-repeat coordinates
// stay inside edge texel centers so hardware-repeat bilinear cannot sample the opposite edge.
float sourceWrap(float x,float mode,float halfTexel) {
 if(mode<0.5)return fract(x);
 if(mode<1.5)return clamp(x,halfTexel,1.0-halfTexel);
 if(mode<2.5)return clamp(1.0-abs(mod(x,2.0)-1.0),halfTexel,1.0-halfTexel);
 return clamp(abs(x),halfTexel,1.0-halfTexel);
}
vec2 png(vec2 p,vec2 modes,ivec2 size) {
 vec2 h=0.5/vec2(max(size,ivec2(1)));
 return vec2(sourceWrap(p.x,modes.x,h.x),1.0-sourceWrap(p.y,modes.y,h.y));
}
vec4 sampleMain(vec2 p){return texture(Sampler0,png(p,WrapMain,textureSize(Sampler0,0)));}
vec4 sampleMask(vec2 p){return texture(Sampler1,png(p,WrapMask,textureSize(Sampler1,0)));}
vec4 sampleNoise(vec2 p){return texture(Sampler2,png(p,WrapNoise,textureSize(Sampler2,0)));}
vec2 st(vec2 uv,vec4 s) {return uv*s.xy+s.zw;}
void main() {
 vec2 uv=texCoord;
 vec4 color;
 if(Params.x>2.5 && Params.x<3.5) {
  vec4 base=sampleMain(st(uv,MainST));
  vec4 flow=sampleMask(vertexColor.rg+vec2(FlowOffset,0.0));
  // Native opaque body-plus-flow replacement; legacy additive-only mode stays available.
  fragColor=Params.w>0.5?vec4(base.rgb+flow.rgb*flow.a*base.rgb,base.a)*Tint:vec4(flow.rgb*base.rgb,flow.a*base.a)*Tint;
  if(fragColor.a<0.003)discard;
  return;
 }
 if(Params.x>1.5 && Params.x<2.5) {
  vec2 uvn=st(uv,NoiseST);
  float n1=sampleNoise(uvn+mod(HeatTime*vec2(EffectTime/20.0,2.0*EffectTime),1.0)).r;
  float n2=sampleNoise(uvn+mod(HeatTime*vec2(EffectTime,EffectTime/20.0),1.0)).r;
  vec4 mask=sampleMask(uv);
  vec2 p=st(uv,MainST)+mask.rg*(n1+n2-1.0)*Force+mod(Scroll.xy*EffectTime,1.0);
  color=sampleMain(p);
  color.rgb*=Params.y;
  // The original _MainTex_A is unassigned: its native default is white.
  color.a=mask.r*Params.y;
 } else if(Params.x>0.5) {
  float n=sampleNoise(st(uv+Scroll.zw*EffectTime,NoiseST)).r;
  bool dynamicMask=Params.x>3.5;
  if(dynamicMask?(n-Custom.z+0.5<0.0):(n+1.0-0.5<0.0))discard;
  vec2 p=dynamicMask?uv+Custom.xy+vec2(n*Custom.w):uv+Scroll.xy*EffectTime+vec2(n*Params.w);
  color=sampleMain(st(p,MainST));
  color.rgb=pow(max(color.rgb*Params.y,vec3(0.0)),vec3(max(0.01,Params.z)));
  color.a*=sampleMask(st(uv,MaskST)).r;
 } else {
  color=sampleMain(st(uv,MainST)+Scroll.xy*EffectTime);
  color.rgb*=Params.y;
 }
 fragColor=color*Tint*vertexColor;
 if(fragColor.a<0.003)discard;
}
