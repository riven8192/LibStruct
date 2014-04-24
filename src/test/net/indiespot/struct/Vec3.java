package test.net.indiespot.struct;

import net.indiespot.struct.cp.CopyStruct;
import net.indiespot.struct.cp.StructType;
import net.indiespot.struct.cp.StructField;

@StructType(sizeof = 12)
public class Vec3 {
	@StructField(offset = 0) public float x;
	@StructField(offset = 4) public float y;
	@StructField(offset = 8) public float z;

	public void add(Vec3 that) {
		this.x += that.x;
		this.y += that.y;
		this.z += that.z;
	}

	public void mul(Vec3 that) {
		this.x *= that.x;
		this.y *= that.y;
		this.z *= that.z;
	}

	@CopyStruct
	public Vec3 copy() {
		return this;
	}

	public static void noob() {
		System.out.println("n00b!");
	}

	@Override
	public String toString() {
		return "Vec3";//"Vec3[" + x + ", " + y + ", " + z + "]";
	}
}
