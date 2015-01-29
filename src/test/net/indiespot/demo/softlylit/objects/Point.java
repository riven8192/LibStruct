package test.net.indiespot.demo.softlylit.objects;

public class Point {

	public float x, y;

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