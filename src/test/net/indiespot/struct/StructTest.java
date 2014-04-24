package test.net.indiespot.struct;

import java.nio.ByteBuffer;
import java.util.Random;

import net.indiespot.struct.cp.CopyStruct;
import net.indiespot.struct.cp.TakeStruct;
import net.indiespot.struct.runtime.IllegalStackAccessError;
import net.indiespot.struct.runtime.StructMemory;

public class StructTest {
	public static void main(String[] args) {
		//TestPerformance.test();
		//TheAgentD.main(args);

		if(!true) {
			TestNull.test();
			TestOneInstance.test();
			TestOneInstanceNull.test();
			TestOneInstanceNullRef.test();
			TestOneInstanceInstanceof.test();
			TestTwoInstances.test();

			TestFields.test();
			TestSum.test();

			TestSetter.test();
			TestMethodPass.test();

			TestIfFlow.test();
			TestLoopFlow.test();

			TestConstructor.test();
			TestTryFinally.test();
			TestTryCatchFinally.test();

			TestArray.test();

			TestInstanceMethod.test();
			TestStructReturnType.test();
			try {
				TestStack.test();
				//throw new IllegalStateException();
			}
			catch (IllegalStackAccessError expected) {
				// ok!
				//expected.printStackTrace();
			}

		}

		//TestMapping.test();
		TestStructField.test();
		TestStructAsObjectParam.test();

		System.out.println("done2");
	}

	public static class TestStructAsObjectParam {
		public static void test() {
			System.out.println("w00t");
			Vec3 vec = new Vec3();

			System.out.println(vec);

			// --

			test(null);
			test(vec);

			test(null, null);
			test(null, vec);
			test(vec, null);
			test(vec, vec);

			test(null, null, null);
			test(null, null, vec);
			test(null, vec, null);
			test(null, vec, vec);
			test(vec, null, null);
			test(vec, null, vec);
			test(vec, vec, null);
			test(vec, vec, vec);

			// --

			test("v");
			test(vec);

			test("v", "v");
			test("v", vec);
			test(vec, "v");
			test(vec, vec);

			test("v", "v", "v");
			test("v", "v", vec);
			test("v", vec, "v");
			test("v", vec, vec);
			test(vec, "v", "v");
			test(vec, "v", vec);
			test(vec, vec, "v");
			test(vec, vec, vec);
		}

		private static void test(Object a) {
			System.out.println("a=" + a);
		}

		private static void test(Object a, Object b) {
			System.out.println("a=" + a);
			System.out.println("b=" + b);
		}

		private static void test(Object a, Object b, Object c) {
			System.out.println("a=" + a);
			System.out.println("b=" + b);
			System.out.println("c=" + c);
		}
	}

	public static class TestNull {
		public static void test() {
			Vec3 vec = null;
			vec = new Vec3();
			System.out.println(vec.toString());
			vec = null;
			vec = new Vec3();
			System.out.println(vec.toString());
		}
	}

	public static class TestStructField {
		public static void test() {
			new TestStructField().testInstance();
			TestStructField.testStatic();
		}

		public Vec3 vec1;

		public void testInstance() {
			vec1 = new Vec3();
			vec1.x = 43.21f;
			Vec3 that = vec1;
			vec1 = that;
			assert (vec1.x == 43.21f);
			assert (that.x == 43.21f);
		}

		public static Vec3 vec2;

		public static void testStatic() {
			vec2 = new Vec3();
			vec2.x = 12.34f;
			Vec3 that = vec2;
			vec2 = that;
			assert (vec2.x == 12.34f);
			assert (that.x == 12.34f);
		}
	}

	public static class TestMapping {
		public static void test() {
			int alignMargin = 4 - 1;
			int sizeof = 3 << 2;
			int count = 10;
			ByteBuffer bb = ByteBuffer.allocateDirect(count * sizeof + alignMargin);
			StructMemory.alignBufferToWord(bb);
			Vec3[] mapped = StructUtil.map(Vec3.class, bb);
			//System.out.println(mapped.length);
			//for(int i = 0; i < mapped.length; i++) {
			//	System.out.println(mapped[i].toString());
			//}
			System.out.println("done:" + bb);
		}
	}

	public static class TestOneInstance {
		public static void test() {
			new Vec3();
		}
	}

	public static class TestOneInstanceNull {
		public static void test() {
			Object obj = new Object();
			Vec3 vec = new Vec3();
			assert !(vec == null);
			assert !(obj == null);
			assert (vec != null);
			assert (obj != null);
			assert (vec == vec);
			assert (obj == obj);
		}
	}

	public static class TestOneInstanceNullRef {
		public static void test() {
			//Vec3 vec = new Vec3();
			//vec.x = 5.6f;
			//echo(vec.x);
			//vec = null; FIXME: very hard to fix
			//echo(vec.x);
		}
	}

	public static class TestOneInstanceInstanceof {
		public static void test() {
			Object obj = new Object();
			Vec3 vec = new Vec3();
			assert !(vec instanceof Vec3);
			assert (obj instanceof Object);
		}
	}

	public static class TestTwoInstances {
		public static void test() {
			Vec3 vec1 = new Vec3();
			Vec3 vec2 = new Vec3();
			assert vec1 != vec2;
		}
	}

	public static class TestFields {
		public static void test() {
			Vec3 vec = new Vec3();
			vec.x = 13.34f;
			vec.y = 14.46f;
			vec.z = 15.56f;
			assert vec.x == 13.34f;
			assert vec.y == 14.46f;
			assert vec.z == 15.56f;
		}
	}

	public static class TestSum {
		public static void test() {
			Vec3 vec1 = new Vec3();
			vec1.x = 13.34f;
			vec1.y = 14.46f;
			vec1.z = 15.58f;

			Vec3 vec2 = new Vec3();
			vec2.x = 1 + vec1.x;
			vec2.y = 2 + vec1.y;
			vec2.z = 3 + vec1.z;

			assert vec2.x == 14.34f;
			assert vec2.y == 16.46f;
			assert vec2.z == 18.58f;
		}
	}

	public static class TestSetter {
		public static void test() {
			Vec3 v = new Vec3();
			assert v.x == 0;
			assert v.y == 0;
			assert v.z == 0;
			Vec3 ref = v.set(4567.8f, 4.5f, 3);
			if(v != ref)
				throw new IllegalStateException();
			assert ref.x == 4567.8f;
			assert ref.y == 4.5f;
			assert ref.z == 3f;
		}
	}

	public static class TestMethodPass {
		public static void test() {
			Vec3 vec = new Vec3();
			vec.x = 3.34f;
			vec.y = 4.46f;
			vec.z = 5.56f;

			test(vec);
		}

		public static void test(Vec3 vec) {
			assert vec.x == 3.34f;
			assert vec.y == 4.46f;
			assert vec.z == 5.56f;
		}
	}

	public static class TestIfFlow {
		public static void test() {
			Vec3 vec = new Vec3();

			if(Math.random() < 0.5)
				return;
			echo(vec.x);
		}

		public static void test2() {
			Vec3 vec = new Vec3();

			if(Math.random() < 0.5)
				echo(vec.x);
			return;
		}

		public static void test3() {
			Vec3 vec = new Vec3();

			if(Math.random() < 0.5)
				echo(vec.x);
			else
				echo(vec.x);
		}
	}

	public static class TestLoopFlow {
		public static void test() {
			Vec3 vec = new Vec3();
			for(int i = 0; i < 3; i++) {
				echo(vec.x);
			}
			echo(vec.x);
		}

		public static void test2() {
			Vec3 vec = new Vec3();
			while (Math.random() < 0.5) {
				echo(vec.x);
			}
			echo(vec.x);
		}

		public static void test3() {
			Vec3 vec = new Vec3();
			do {
				echo(vec.x);
			}
			while (Math.random() < 0.5);
			echo(vec.x);
		}

		public static void test4() {
			for(int i = 0; i < 3; i++) {
				echo(new Vec3().x);
			}
		}

		public static void test5() {
			while (Math.random() < 0.5) {
				echo(new Vec3().x);
			}
		}

		public static void test6() {
			do {
				echo(new Vec3().x);
			}
			while (Math.random() < 0.5);
		}
	}

	public static class TestConstructor {
		public static void test() {
			Vec3 v;

			v = new Vec3();
			assert v.x == 0.0f;
			assert v.y == 0.0f;
			assert v.z == 0.0f;

			v = new Vec3(1, 2, 3);
			assert v.x == 1;
			assert v.y == 2;
			assert v.z == 3;

			v = new Vec3(1.2f, 3.4f, 5.6f);
			assert v.x == 1.2f;
			assert v.y == 3.4f;
			assert v.z == 5.6f;

			v = new Vec3(1337f);
			assert v.x == 1337f;
			assert v.y == 1337f;
			assert v.z == 1337f;
		}
	}

	public static class TestTryFinally {
		public static void test() {
			Vec3 a = new Vec3();

			try {
				a.x = 5;
			}
			finally {
				a.y = 6;
			}
		}
	}

	public static class TestTryCatchFinally {
		public static void test() {
			Vec3 a = new Vec3();

			try {
				a.x = 13;
			}
			catch (Throwable t) {

			}
			finally {
				a.y = 14;
			}

			try {
				a.x = 13;
			}
			catch (Throwable t) {
				System.out.println(t);
			}
			finally {
				a.y = 14;
			}

			try {
				a.x = 13;
			}
			catch (Throwable t) {
				throw t;
			}
			finally {
				a.y = 14;
			}

			try {
				a.x = 13;
			}
			catch (Throwable t) {
				System.out.println(t);
				throw new IllegalStateException("doh!");
			}
			finally {
				a.y = 14;
			}
		}
	}

	public static class TestAddress {
		public static void test() {
			Vec3 vec = new Vec3();
			Object obj = new Object();

			System.out.println("addr=" + StructUtil.getPointer(vec));
			System.out.println("addr=" + StructUtil.getPointer(obj));
		}
	}

	public static class TestInstanceMethod {
		public static void test() {
			Vec3 a = new Vec3();
			a.x = 5;

			Vec3 b = new Vec3();
			b.x = 6;

			Vec3 vec = new Vec3();
			vec.add(a);
			vec.add(b);

			echo(vec.x);

			Vec3 copy = vec.copy();
			echo(copy.x);

			copy.x = 15;
			echo(copy.x);
			echo(vec.x);
		}
	}

	public static class TestStructReturnType {
		public static void test() {
			Vec3 vec1 = new Vec3();
			vec1.x = 13.37f;
			Vec3 vec2 = returnSelf(vec1);
			Vec3 vec3 = returnCopy(vec1);

			System.out.println(StructUtil.getPointer(vec1));
			System.out.println(StructUtil.getPointer(vec2));
			System.out.println(StructUtil.getPointer(vec3));

			if(vec1 != vec2)
				throw new IllegalStateException("vec1 != vec2");
			if(vec1 == vec3)
				throw new IllegalStateException("vec1 == vec3");

			System.out.println(vec1.x);
			System.out.println(vec2.x);
			vec1.x = 73.31f;
			System.out.println(vec1.x);
			System.out.println(vec2.x);
		}

		@TakeStruct
		public static Vec3 returnSelf(Vec3 vec) {
			return vec;
		}

		@CopyStruct
		public static Vec3 returnCopy(Vec3 vec) {
			return vec;
		}
	}

	public static class TestStack {
		public static void test() {
			Vec3 vec = new Vec3();
			vec.x = 1;

			vec = self(vec);
			vec.x = 3;
			if(vec.y != 13)
				throw new IllegalStateException();

			vec = copy();
			vec.x = 4;
			if(vec.y != 14)
				throw new IllegalStateException();

			vec = pass();
			vec.x = 5; // must crash, as the struct is on the part of the stack that was popped
			if(vec.y != 15)
				throw new IllegalStateException();
		}

		@TakeStruct
		private static Vec3 self(Vec3 v) {
			v.y = 13;
			return v;
		}

		@CopyStruct
		private static Vec3 copy() {
			Vec3 v = new Vec3();
			v.y = 14;
			return v;
		}

		@TakeStruct
		private static Vec3 pass() {
			Vec3 v = new Vec3();
			v.y = 15;
			return v;
		}
	}

	public static class TestArray {
		public static void test() {
			Vec3[] arr = new Vec3[10];
			arr[arr.length - 1].x = 4.5f;
			arr[arr.length - 1].set(5.6f, 6.7f, 7.8f);

			Vec3 a = arr[0];
			Vec3 b = arr[1];

			a.x = 3.7f;
			b.x = 4.8f;
			assert a.x == arr[0].x;
			assert b.x == arr[1].x;

			System.out.println(a.x);
			System.out.println(b.x);
			System.out.println(arr[arr.length - 1].x);
		}
	}

	// ----------------

	public static class TestPerformance {
		public static void test() {

			Random rndm = new Random(12153);
			NormalVec3 nv = new NormalVec3();
			Vec3 sv = new Vec3();

			NormalVec3[] arr2 = new NormalVec3[1024];
			Vec3[] arr3 = new Vec3[1024];
			Vec3[] arr4 = new Vec3[1024];

			for(int k = 0; k < 1024; k++) {
				System.out.println();
				float p = 0;
				sv.x = nv.x = rndm.nextFloat();
				sv.y = nv.y = rndm.nextFloat();
				sv.z = nv.z = rndm.nextFloat();

				long[] tA = new long[32];
				long[] tB = new long[32];

				// ---

				for(int i = 0; i < tA.length; i++) {
					long t0 = System.nanoTime();
					benchInstanceNew(arr2);
					long t1 = System.nanoTime();
					tA[i] = t1 - t0;
				}

				for(int i = 0; i < tB.length; i++) {
					long t0 = System.nanoTime();
					benchStructNew(arr3);
					long t1 = System.nanoTime();
					tB[i] = t1 - t0;
				}

				System.out.println("instance creation:       " + tA[tA.length / 2] / 1000 + "us [" + p + "]");
				System.out.println("struct creation:         " + tB[tB.length / 2] / 1000 + "us [" + p + "]");

				// ---

				for(int i = 0; i < tA.length; i++) {
					long t0 = System.nanoTime();
					p += benchInstanceAccess(nv);
					long t1 = System.nanoTime();
					tA[i] = t1 - t0;
				}

				for(int i = 0; i < tB.length; i++) {
					long t0 = System.nanoTime();
					p += benchStructAccess(sv);
					long t1 = System.nanoTime();
					tB[i] = t1 - t0;
				}

				System.out.println("instances access:        " + tA[tA.length / 2] / 1000 + "us [" + p + "]");
				System.out.println("struct access:           " + tB[tB.length / 2] / 1000 + "us [" + p + "]");

				// ---

				for(int i = 0; i < tA.length; i++) {
					long t0 = System.nanoTime();
					p += benchInstanceArray(arr2);
					long t1 = System.nanoTime();
					tA[i] = t1 - t0;
				}

				for(int i = 0; i < tB.length; i++) {
					long t0 = System.nanoTime();
					p += benchStructArray(arr4);
					long t1 = System.nanoTime();
					tB[i] = t1 - t0;
				}

				System.out.println("instance array access:   " + tA[tA.length / 2] / 1000 + "us [" + p + "]");
				System.out.println("struct array access:     " + tB[tB.length / 2] / 1000 + "us [" + p + "]");
			}
		}

		private static void benchInstanceNew(NormalVec3[] arr) {
			for(int i = 0; i < 128; i++) {
				benchInstanceNew2(arr);
			}
		}

		private static void benchInstanceNew2(NormalVec3[] arr) {
			for(int i = 0; i < arr.length; i++) {
				arr[i] = new NormalVec3();
			}
		}

		private static void benchStructNew(Vec3[] arr) {
			for(int i = 0; i < 128; i++) {
				benchStructNew2(arr);
			}
		}

		private static void benchStructNew2(Vec3[] arr) {
			for(int i = 0; i < arr.length; i++) {
				arr[i] = new Vec3();
			}
		}

		private static float benchInstanceAccess(NormalVec3 nv) {
			float p = 0;
			for(int i = 0; i < 1024 * 1024; i++) {
				p += nv.x * nv.y + nv.z;
				p *= nv.y * nv.z + nv.x;
				p -= nv.z * nv.x + nv.y;
			}
			return p;
		}

		private static float benchStructAccess(Vec3 nv) {
			float p = 0;
			for(int i = 0; i < 1024 * 1024; i++) {
				p += nv.x * nv.y + nv.z;
				p *= nv.y * nv.z + nv.x;
				p -= nv.z * nv.x + nv.y;
			}
			return p;
		}

		private static float benchInstanceArray(NormalVec3[] arr) {
			float p = 0;
			for(int k = 0; k < 64; k++) {
				for(int i = 0, len = arr.length - 2; i < len; i++) {
					p += arr[i + 0].x * arr[i + 0].y + arr[i + 0].z;
					p *= arr[i + 1].y * arr[i + 1].z + arr[i + 1].x;
					p -= arr[i + 2].z * arr[i + 2].x + arr[i + 2].y;
				}
			}
			return p;
		}

		private static float benchStructArray(Vec3[] arr) {
			float p = 0;
			for(int k = 0; k < 64; k++) {
				for(int i = 0, len = arr.length - 2; i < len; i++) {
					p += arr[i + 0].x * arr[i + 0].y + arr[i + 0].z;
					p *= arr[i + 1].y * arr[i + 1].z + arr[i + 1].x;
					p -= arr[i + 2].z * arr[i + 2].x + arr[i + 2].y;
				}
			}
			return p;
		}
	}

	// ----------------

	public static void echo(String v) {
		System.out.println(v);
	}

	public static void echo(float v) {
		System.out.println(v);
	}

	public static void echo(boolean v) {
		System.out.println(v);
	}
}
