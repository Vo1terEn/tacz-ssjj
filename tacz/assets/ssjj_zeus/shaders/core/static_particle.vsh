#version 150

#moj_import <fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat4 EffectMat;
uniform mat4 ParticleMat;
uniform vec4 ParticleColor;
uniform vec4 ParticleUV;
uniform mat3 IViewRotMat;
uniform int FogShape;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;

void main() {
    vec3 position = (EffectMat * ParticleMat * vec4(Position, 1.0)).xyz;
    gl_Position = ProjMat * ModelViewMat * vec4(position, 1.0);

    vertexDistance = fog_distance(ModelViewMat, IViewRotMat * position, FogShape);
    vertexColor = ParticleColor;
    texCoord0 = UV0 * ParticleUV.xy + vec2(ParticleUV.z, -ParticleUV.w);
}
