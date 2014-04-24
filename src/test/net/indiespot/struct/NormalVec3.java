package test.net.indiespot.struct;

public class NormalVec3 {
	public float x, y, z;

	public void add(NormalVec3 that) {
		this.x += that.x;
		this.y += that.y;
		this.z += that.z;
	}

	public void mul(NormalVec3 that) {
		this.x *= that.x;
		this.y *= that.y;
		this.z *= that.z;
	}

	public static NormalVec3 stackAlloc() {
		return null;
	}

	public static void noob() {
		System.out.println("n00b!");
	}

	@Override
	public String toString() {
		return "Vec3[" + x + ", " + y + ", " + z + "]";
	}
}
