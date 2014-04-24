package test.net.indiespot.struct;

import net.indiespot.struct.cp.CopyStruct;
import net.indiespot.struct.cp.TakeStruct;
import net.indiespot.struct.runtime.StructAllocationStack;
import net.indiespot.struct.runtime.StructThreadLocalStack;

public class TheAgentD {

	private static int iterations = 100_000_000;
	private static int tests = 16;

	public static void main(String[] args) {
		for(int t = 0; t < tests; t++) {
			testVec3((float) Math.random());
			testNormalVec3((float) Math.random());
			testLocalVarsVec3((float) Math.random());
		}
	}

	private static void testVec3(float start) {
		long time = System.nanoTime();

		//Vec3 tmp = new Vec3();

		//float[] arr = new float[16];
		//Random rndm = new Random(12345L);
		//for(int i = 0; i < arr.length; i++)
		//	arr[i] = rndm.nextFloat();

		Vec3 vec3 = new Vec3(start, start, start);
		for(int i = 0; i < iterations; i++) {
			StructAllocationStack sas = StructThreadLocalStack.saveStack();

			//vec3.add(vec3(1, 2, 3)).mul(vec3(0.75f, 0.75f, 0.75f));

			vec3.add(new Vec3(1, 2, 3)).mul(new Vec3(0.75f, 0.75f, 0.75f));
			vec3.add(new Vec3(1, 2, 3)).mul(new Vec3(0.75f, 0.75f, 0.75f));
			vec3.add(new Vec3(1, 2, 3)).mul(new Vec3(0.75f, 0.75f, 0.75f));

			//			vec3.add(tmp.set(1, 2, 3)).mul(tmp.set(0.75f, 0.75f, 0.75f));
			//			vec3.add(tmp.set(1, 2, 3)).mul(tmp.set(0.75f, 0.75f, 0.75f));
			//			vec3.add(tmp.set(1, 2, 3)).mul(tmp.set(0.75f, 0.75f, 0.75f));

			//			vec3.add(tmp.set(arr[p++ & 0xF], arr[p++ & 0xF], arr[p++ & 0xF])).mul(tmp.set(arr[p++ & 0xF], arr[p++ & 0xF], arr[p++ & 0xF]));
			//			vec3.add(tmp.set(arr[p++ & 0xF], arr[p++ & 0xF], arr[p++ & 0xF])).mul(tmp.set(arr[p++ & 0xF], arr[p++ & 0xF], arr[p++ & 0xF]));
			//			vec3.add(tmp.set(arr[p++ & 0xF], arr[p++ & 0xF], arr[p++ & 0xF])).mul(tmp.set(arr[p++ & 0xF], arr[p++ & 0xF], arr[p++ & 0xF]));

			sas.restore();
		}

		System.out.println((System.nanoTime() - time) / 1000 / 1000f + ": " + vec3.toString());
	}

	private static void testNormalVec3(float start) {
		long time = System.nanoTime();

		//float[] arr = new float[16];
		//Random rndm = new Random(12345L);
		//for(int i = 0; i < arr.length; i++)
		//	arr[i] = rndm.nextFloat();

		NormalVec3 vec3 = nvec3(start, start, start);
		for(int i = 0; i < iterations; i++) {
			vec3.add(nvec3(1, 2, 3)).mul(nvec3(0.75f, 0.75f, 0.75f));
			vec3.add(nvec3(1, 2, 3)).mul(nvec3(0.75f, 0.75f, 0.75f));
			vec3.add(nvec3(1, 2, 3)).mul(nvec3(0.75f, 0.75f, 0.75f));

			//			vec3.add(nvec3(arr[p++ & 0xF], arr[p++ & 0xF], arr[p++ & 0xF])).mul(nvec3(arr[p++ & 0xF], arr[p++ & 0xF], arr[p++ & 0xF]));
			//			vec3.add(nvec3(arr[p++ & 0xF], arr[p++ & 0xF], arr[p++ & 0xF])).mul(nvec3(arr[p++ & 0xF], arr[p++ & 0xF], arr[p++ & 0xF]));
			//			vec3.add(nvec3(arr[p++ & 0xF], arr[p++ & 0xF], arr[p++ & 0xF])).mul(nvec3(arr[p++ & 0xF], arr[p++ & 0xF], arr[p++ & 0xF]));
		}

		System.out.println((System.nanoTime() - time) / 1000 / 1000f + ": " + vec3.toString());

	}

	private static void testLocalVarsVec3(float start) {
		long time = System.nanoTime();

		//float[] arr = new float[16];
		//Random rndm = new Random(12345L);
		//for(int i = 0; i < arr.length; i++)
		//	arr[i] = rndm.nextFloat();

		float vx = start;
		float vy = start;
		float vz = start;

		for(int i = 0; i < iterations; i++) {
			{
				float ax = 1;
				float ay = 2;
				float az = 3;
				vx += ax;
				vy += ay;
				vz += az;

				float bx = 0.75f;
				float by = 0.75f;
				float bz = 0.75f;
				vx *= bx;
				vy *= by;
				vz *= bz;
			}
			{
				float ax = 1;
				float ay = 2;
				float az = 3;
				vx += ax;
				vy += ay;
				vz += az;

				float bx = 0.75f;
				float by = 0.75f;
				float bz = 0.75f;
				vx *= bx;
				vy *= by;
				vz *= bz;
			}
			{
				float ax = 1;
				float ay = 2;
				float az = 3;
				vx += ax;
				vy += ay;
				vz += az;

				float bx = 0.75f;
				float by = 0.75f;
				float bz = 0.75f;
				vx *= bx;
				vy *= by;
				vz *= bz;
			}
		}

		System.out.println((System.nanoTime() - time) / 1000 / 1000f + ": LocalVarsVec3[" + vx + "," + vy + "," + vz + "]");
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