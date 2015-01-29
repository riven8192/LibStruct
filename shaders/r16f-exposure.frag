varying vec2 texCoord;

uniform float exposure;
uniform sampler2D myTextureSampler;

void main() {
	float rndm = gl_FragCoord.x * 353.0 + gl_FragCoord.y * 769.0;
	float noise = fract(rndm / 991.0) / 255.0;

	const float grid = 1.0 / 512;

	float light = texture(myTextureSampler, texCoord).r;
	light = 1.0 - exp(light * -exposure);
	gl_FragColor = vec4(vec3(light + noise), 1.0);
}