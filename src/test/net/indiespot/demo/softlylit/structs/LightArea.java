package test.net.indiespot.demo.softlylit.structs;

import net.indiespot.struct.cp.Struct;

public class LightArea {
	public Point origin;
	public float radius;
	public int triangleCount;

	final TriangleList circleTris = new TriangleList();
	TriangleList litArea = new TriangleList();

	public void sync() {
		circleTris.clear();

		for(int i = 0; i < triangleCount; i++) {
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

			tri.sync();
			circleTris.add(tri);
		}
	}

	public void clear() {
		litArea.clear();
		litArea.addAll(circleTris);
	}

	public TriangleList occlude(Line occluder, TriangleList newLitArea, TriangleList out) {

		TriangleList litArea = this.litArea;
		{
			float x = origin.x;
			float y = origin.y;
			float r = radius;
			Point p1 = occluder.p1;
			Point p2 = occluder.p2;
			float dx = x - p1.x;
			float dy = y - p1.y;
			if(dx * dx + dy * dy > r * r) {
				dx = x - p2.x;
				dy = y - p2.y;
				if(dx * dx + dy * dy > r * r) {

					newLitArea.clear();
					for(int i = 0, len = litArea.size(); i < len; i++) {
						newLitArea.add(litArea.get(i));
					}

					// swap
					TriangleList tmp = litArea;
					this.litArea = litArea = newLitArea;
					newLitArea = tmp;

					return newLitArea;
				}
			}
		}

		newLitArea.clear();
		for(int i = 0, len = litArea.size(); i < len; i++) {
			Demo.occlude(litArea.get(i), occluder, out);
			for(int j = 0, len2 = out.size(); j < len2; j++) {
				Triangle lit = out.get(j);
				lit.sync();
				newLitArea.add(lit);
			}
		}

		// swap
		TriangleList tmp = litArea;
		this.litArea = litArea = newLitArea;
		newLitArea = tmp;

		return newLitArea;
	}
}