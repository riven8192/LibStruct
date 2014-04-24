package test.net.indiespot.struct;

import net.indiespot.struct.cp.CopyStruct;
import net.indiespot.struct.cp.TakeStruct;

public class TheAgentD {

	private static int iterations = 10_000;
	private static int tests = 16;

	public static void main(String[] args) {

		for(int t = 0; t < tests; t++) {
			testVec3((float) Math.random());
			testNormalVec3((float) Math.random());
		}
	}

	private static void testVec3(float start) {
		long time = System.nanoTime();

		Vec3 vec3 = vec3(start, start, start);
		for(int i = 0; i < iterations; i++) {
			vec3.add(vec3(1, 2, 3)).mul(vec3(0.75f, 0.75f, 0.75f));
			//update(vec3);
		}

		System.out.println((System.nanoTime() - time) / 1000 / 1000f + ": " + vec3.toString());
	}

	private static void testNormalVec3(float start) {
		long time = System.nanoTime();

		NormalVec3 vec3 = nvec3(start, start, start);
		for(int i = 0; i < iterations; i++) {
			vec3.add(nvec3(1, 2, 3)).mul(nvec3(0.75f, 0.75f, 0.75f));
		}

		System.out.println((System.nanoTime() - time) / 1000 / 1000f + ": " + vec3.toString());

	}

	@TakeStruct
	private static void update(Vec3 vec3) {
		vec3.add(vec3(1, 2, 3)).mul(vec3(0.75f, 0.75f, 0.75f));
	}

	@CopyStruct
	public static Vec3 vec3(float x, float y, float z) {
		return new Vec3(x, y, z);
	}

	public static NormalVec3 nvec3(float x, float y, float z) {
		return new NormalVec3(x, y, z);
	}
}