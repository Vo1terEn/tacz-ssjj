#version 150
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform sampler2D Sampler3;
uniform sampler2D SceneSampler;
uniform vec2 ScreenSize;
uniform vec4 ModelST;
uniform vec4 ModelMaskST;
uniform vec4 ModelPan;
uniform vec4 ModelColor;
uniform vec4 Refraction;
uniform float Dis;
uniform float EffectTime;
uniform float LegacyMode;
uniform float AdditiveEnergy;
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
in vec4 vertexColor;
in vec2 texCoord;
out vec4 fragColor;
vec2 png(vec2 p) { return vec2(p.x,1.0-p.y); }
vec2 st(vec2 uv,vec4 s) {return uv*s.xy+s.zw;}
void main() {
 vec2 uv=texCoord;
 vec4 color;
 if(Params.x>5.5 && Params.x<6.5) {
  vec4 model=texture(Sampler3,png(st(uv+ModelPan.xy*EffectTime,ModelST)));
  vec4 modelMask=texture(Sampler1,png(st(uv+ModelPan.zw*EffectTime,ModelMaskST)));
  if(model.r*vertexColor.a*Dis-.5<0.0)discard;
  float n=texture(Sampler2,png(st(uv+Scroll.zw*EffectTime,NoiseST))).r;
  vec4 mainTex=texture(Sampler0,png(st(uv+Scroll.xy*EffectTime+vec2(n*Params.w),MainST)));
  vec3 glow=pow(max(modelMask.rgb*mainTex.rgb*Params.y,vec3(0.0)),vec3(Params.z))*Tint.rgb;
  fragColor=vec4((glow+ModelColor.rgb*model.rgb)*vertexColor.rgb,mainTex.a*vertexColor.a*Tint.a);
  if(AdditiveEnergy>0.5){fragColor.rgb*=max(1.0,fragColor.a);fragColor.a=min(1.0,fragColor.a);}
  fragColor.a=clamp(fragColor.a,0.0,1.0);
  return;
 }
 if(Params.x>4.5 && Params.x<5.5) {
  vec2 dist=textureLod(Sampler0,png(st(uv,MainST)),Refraction.x).rg;
  // Composite the disturbed interior of the shared pre-effect scene; zero
  // source alpha and the card boundary must leave the current target intact.
  float edge=min(min(uv.x,1.0-uv.x),min(uv.y,1.0-uv.y));
  float coverage=smoothstep(0.01,0.15,max(dist.r,dist.g))
      *smoothstep(0.0,0.12,edge)*clamp(vertexColor.a,0.0,1.0);
  if(coverage<0.003)discard;
  vec2 screen=gl_FragCoord.xy/ScreenSize;
  vec3 grab=texture(SceneSampler,clamp(screen+dist*vertexColor.a*Refraction.y,vec2(0.0),vec2(1.0))).rgb;
  fragColor=vec4(grab*(1.0-Refraction.z),coverage);
  return;
 }
 if(Params.x>2.5 && Params.x<3.5) {
  vec4 base=texture(Sampler0,png(st(uv,MainST)));
  vec4 flow=texture(Sampler1,png(vertexColor.rg+vec2(FlowOffset,0.0)));
  // Base is already drawn by the native gun pass. Add only the native flow term.
  fragColor=vec4(flow.rgb*base.rgb,flow.a*base.a)*Tint;
  fragColor.a=clamp(fragColor.a,0.0,1.0);
 if(fragColor.a<0.003)discard;
  return;
 }
 if(Params.x>1.5 && Params.x<2.5) {
  vec2 uvn=st(uv,NoiseST);
  float n1=texture(Sampler2,png(uvn+mod(HeatTime*vec2(EffectTime/20.0,2.0*EffectTime),1.0))).r;
  float n2=texture(Sampler2,png(uvn+mod(HeatTime*vec2(EffectTime,EffectTime/20.0),1.0))).r;
  vec4 mask=texture(Sampler1,png(uv));
  vec2 p=st(uv,MainST)+mask.rg*(n1+n2-1.0)*Force+mod(Scroll.xy*EffectTime,1.0);
  color=texture(Sampler0,png(p));
  color.rgb*=Params.y;
  // The original _MainTex_A is unassigned: its native default is white.
  color.a=mask.r*Params.y;
 } else if(Params.x>0.5) {
  float n=texture(Sampler2,png(st(uv+Scroll.zw*EffectTime,NoiseST))).r;
  bool dynamicMask=Params.x>3.5;
  if(dynamicMask?(n-Custom.z+0.5<0.0):(n+Dis-0.5<0.0))discard;
  vec2 p=dynamicMask?uv+Custom.xy+vec2(n*Custom.w):uv+Scroll.xy*EffectTime+vec2(n*Params.w);
  color=texture(Sampler0,png(st(p,MainST)));
  color.rgb=pow(max(color.rgb*Params.y,vec3(0.0)),vec3(max(0.01,Params.z)));
  color.a*=texture(Sampler1,png(st(uv,MaskST))).r;
 } else {
  color=texture(Sampler0,png(st(uv,MainST)+Scroll.xy*EffectTime));
  color.rgb*=Params.y;
 }
 fragColor=color*Tint*vertexColor;
 if(LegacyMode>0.5){fragColor.rgb*=fragColor.a;fragColor.a=clamp(fragColor.a,0.0,1.0);}
 if(AdditiveEnergy>0.5){fragColor.rgb*=max(1.0,fragColor.a);fragColor.a=min(1.0,fragColor.a);}
 fragColor.a=clamp(fragColor.a,0.0,1.0);
 if(fragColor.a<0.003)discard;
}
