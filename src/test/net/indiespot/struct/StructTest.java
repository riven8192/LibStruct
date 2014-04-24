package test.net.indiespot.struct;

import net.indiespot.struct.cp.CopyStruct;
import net.indiespot.struct.cp.TakeStruct;

public class StructTest {
	public static void main(String[] args) {
		System.out.println(Vec3.class.getName());

		if(true) {
			TestOneInstance.test();
			TestOneInstanceNull.test();
			TestOneInstanceNullRef.test();
			TestOneInstanceInstanceof.test();
			TestTwoInstances.test();
			TestFields.test();
			TestSetter.test();
			TestSum.test();
			TestMethodPass.test();
			TestIfFlow.test();
			TestLoopFlow.test();
			TestAddress.test();
			TestInstanceMethod.test();
			TestStructReturnType.test();
			TestStack.test();
		}

		TestSetter.test();
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
			Vec3 vec = new Vec3();
			vec.x = 5.6f;
			echo(vec.x);
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
			echo(v.x);
			echo(v.y);
			echo(v.z);
			Vec3 ref = v.set(4567.8f, 4.5f, 3);
			if(v != ref)
				throw new IllegalStateException();
			echo(v.x);
			echo(v.y);
			echo(v.z);
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

	public static class TestAddress {
		public static void test() {
			Vec3 vec = new Vec3();
			Object obj = new Object();

			System.out.println("addr=" + StructUtil.getPointer(vec));
			//System.out.println("addr=" + StructUtil.getPointer(obj));
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
				throw new IllegalStateException();
			if(vec1 == vec3)
				throw new IllegalStateException();

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
