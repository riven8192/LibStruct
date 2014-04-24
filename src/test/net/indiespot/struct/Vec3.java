package test.net.indiespot.struct;

import net.indiespot.struct.cp.CopyStruct;
import net.indiespot.struct.cp.StructType;
import net.indiespot.struct.cp.StructField;
import net.indiespot.struct.cp.TakeStruct;

@StructType(sizeof = 12)
public class Vec3 {
	@StructField(offset = 0) public float x;
	@StructField(offset = 4) public float y;
	@StructField(offset = 8) public float z;

	public Vec3() {

	}

	public Vec3(float x, float y, float z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	public Vec3(float xyz) {
		this(xyz, xyz, xyz);
	}

	@TakeStruct
	public Vec3 add(Vec3 that) {
		this.x += that.x;
		this.y += that.y;
		this.z += that.z;
		return this;
	}

	@TakeStruct
	public Vec3 mul(Vec3 that) {
		this.x *= that.x;
		this.y *= that.y;
		this.z *= that.z;
		return this;
	}

	@TakeStruct
	public Vec3 set(float x, float y, float z) {
		this.x = x;
		this.y = y;
		this.z = z;
		return this;
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
		return "Vec3[" + x + ", " + y + ", " + z + "]";
	}
}
