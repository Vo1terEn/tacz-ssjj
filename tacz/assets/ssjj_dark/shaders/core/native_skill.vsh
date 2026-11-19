#version 150
in vec3 Position;
in vec2 UV0;
in vec4 Color;
uniform mat4 ProjMat;
out vec2 texUV;
out vec4 vertexColor;
void main(){gl_Position=ProjMat*vec4(Position,1);texUV=UV0;vertexColor=Color;}
