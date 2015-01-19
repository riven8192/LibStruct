package test.net.indiespot.demo.softlylit.structs;

import test.net.indiespot.demo.softlylit.structs.support.TriangleBlock;
import net.indiespot.struct.cp.Struct;

public class LightArea {
	public Point origin;
	public float radius;
	public int triangleCount;

	TriangleBlock circleTris;
	TriangleBlock litArea = new TriangleBlock(64);

	public void sync() {
		circleTris = new TriangleBlock(triangleCount);

		for (int i = 0; i < triangleCount; i++) {
			float angle1 = (i + 0) / (float) triangleCount * (float) Math.PI * 2.0f;
			float angle2 = (i + 1) / (float) triangleCount * (float) Math.PI * 2.0f;

			Triangle tri = Struct.stackAlloc(Demo.STACK, Triangle.class);
			tri.a.load(origin);
			Point b = tri.b;
			Point c = tri.c;
			b.x = origin.x + (float) Math.cos(angle1) * radius;
			b.y = origin.y + (float) Math.sin(angle1) * radius;
			c.x = origin.x + (float) Math.cos(angle2) * radius;
			c.y = origin.y + (float) Math.sin(angle2) * radius;

			circleTris.add(tri);
		}
	}

	public void clear() {
		litArea.clear();
		litArea.addAll(circleTris);
	}

	public void occlude(Line occluder, TriangleBlock tmp) {

		TriangleBlock litArea = this.litArea;
		{
			float x = origin.x;
			float y = origin.y;
			float r = radius;
			Point p1 = occluder.p1;
			Point p2 = occluder.p2;
			float dx = x - p1.x;
			float dy = y - p1.y;
			if (dx * dx + dy * dy > r * r) {
				dx = x - p2.x;
				dy = y - p2.y;
				if (dx * dx + dy * dy > r * r) {
					return;
				}
			}
		}

		tmp.clear();
		for (int i = 0, len = litArea.size(); i < len; i++) {
			Demo.occlude(litArea.get(i), occluder, tmp);
		}

		litArea.clear();
		litArea.addAll(tmp);
	}
}