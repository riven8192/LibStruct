package test.net.indiespot.struct;

public class NormalVec3 {
	public float x, y, z;

	public NormalVec3() {

	}

	public NormalVec3(float x, float y, float z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	public NormalVec3 add(NormalVec3 that) {
		this.x += that.x;
		this.y += that.y;
		this.z += that.z;
		return this;
	}

	public NormalVec3 mul(NormalVec3 that) {
		this.x *= that.x;
		this.y *= that.y;
		this.z *= that.z;
		return this;
	}

	public static NormalVec3 stackAlloc() {
		return null;
	}

	public static void noob() {
		System.out.println("n00b!");
	}

	@Override
	public String toString() {
		return "NormalVec3[" + x + ", " + y + ", " + z + "]";
	}
}
