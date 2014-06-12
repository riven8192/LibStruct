varying vec2 texCoord;

uniform sampler2DArray myTextureSampler;

void main(){
	texCoord = gl_MultiTexCoord0;
    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
}