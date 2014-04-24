package test.net.indiespot.struct;

import net.indiespot.struct.runtime.StructAllocationStack;
import net.indiespot.struct.runtime.StructThreadLocalStack;

public class TheAgentD {

	private static int iterations = 100_000_000;
	private static int tests = 16;

	public static void main(String[] args) {
		for(int t = 0; t < tests; t++) {
			System.out.println();
			testVec3((float) Math.random());
			testNormalVec3((float) Math.random());
			testLocalVarsVec3((float) Math.random());
		}
	}

	private static void testVec3(float start) {
		long time = System.nanoTime();

		StructTest.Vec3 vec3 = new StructTest.Vec3(start, start, start);
		for(int i = 0; i < iterations; i++) {
			StructAllocationStack sas = StructThreadLocalStack.saveStack();

			vec3.add(new StructTest.Vec3(1, 2, 3)).mul(new StructTest.Vec3(0.75f, 0.75f, 0.75f));
			vec3.add(new StructTest.Vec3(1, 2, 3)).mul(new StructTest.Vec3(0.75f, 0.75f, 0.75f));
			vec3.add(new StructTest.Vec3(1, 2, 3)).mul(new StructTest.Vec3(0.75f, 0.75f, 0.75f));

			sas.restore();
		}

		System.out.println((System.nanoTime() - time) / 1000 / 1000f + ": " + vec3.toString());
	}

	private static void testNormalVec3(float start) {
		long time = System.nanoTime();

		NormalVec3 vec3 = new NormalVec3(start, start, start);
		for(int i = 0; i < iterations; i++) {
			vec3.add(new NormalVec3(1, 2, 3)).mul(new NormalVec3(0.75f, 0.75f, 0.75f));
			vec3.add(new NormalVec3(1, 2, 3)).mul(new NormalVec3(0.75f, 0.75f, 0.75f));
			vec3.add(new NormalVec3(1, 2, 3)).mul(new NormalVec3(0.75f, 0.75f, 0.75f));
		}

		System.out.println((System.nanoTime() - time) / 1000 / 1000f + ": " + vec3.toString());
	}

	private static void testLocalVarsVec3(float start) {
		long time = System.nanoTime();

		float vx = start;
		float vy = start;
		float vz = start;

		for(int i = 0; i < iterations; i++) {
			{
				vx += 1;
				vy += 2;
				vz += 3;

				vx *= 0.75f;
				vy *= 0.75f;
				vz *= 0.75f;
			}
			{
				vx += 1;
				vy += 2;
				vz += 3;

				vx *= 0.75f;
				vy *= 0.75f;
				vz *= 0.75f;
			}
			{
				vx += 1;
				vy += 2;
				vz += 3;

				vx *= 0.75f;
				vy *= 0.75f;
				vz *= 0.75f;
			}
		}

		System.out.println((System.nanoTime() - time) / 1000 / 1000f + ": LocalVarsVec3[" + vx + "," + vy + "," + vz + "]");
	}
}