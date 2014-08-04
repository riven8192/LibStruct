package test.net.indiespot.demo.softlylit.structs;

import net.indiespot.struct.cp.StructField;
import net.indiespot.struct.cp.StructType;

@StructType
public class Point {
	@StructField
	public float x;
	@StructField
	public float y;

	private Point() {
		// hide
	}

	public void load(Point src) {
		x = src.x;
		y = src.y;
	}

	public void add(float dx, float dy) {
		x += dx;
		y += dy;
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