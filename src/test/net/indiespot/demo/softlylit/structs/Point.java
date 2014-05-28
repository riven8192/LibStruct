package test.net.indiespot.demo.softlylit.structs;

import net.indiespot.struct.cp.StructField;
import net.indiespot.struct.cp.StructType;

@StructType(sizeof = 8)
public class Point {
	@StructField(offset = 0) public float x;
	@StructField(offset = 4) public float y;

	private Point() {
		// hide
	}

	public void load(Point src) {
		x = src.x;
		y = src.y;
	}

	@Override
	public String toString() {
		return "[" + x + ", " + y + "]";
	}

	public static float distance(Point a, Point b) {
		float dx = a.x - b.x;
		float dy = a.y - b.y;
		return (float) Math.sqrt(dx * dx + dy * dy);
	}
}