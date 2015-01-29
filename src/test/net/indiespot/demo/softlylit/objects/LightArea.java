package test.net.indiespot.demo.softlylit.objects;

import java.util.ArrayList;
import java.util.List;

public class LightArea {
	public Point origin;
	public float radius;
	public int triangleCount;

	private final List<Triangle> circleTris = new ArrayList<>();
	List<Triangle> litArea = new ArrayList<>();

	public void sync() {
		circleTris.clear();

		for(int i = 0; i < triangleCount; i++) {
			float angle1 = (i + 0) / (float) triangleCount * (float) Math.PI * 2.0f;
			float angle2 = (i + 1) / (float) triangleCount * (float) Math.PI * 2.0f;

			Triangle tri = new Triangle();
			tri.a.load(origin);
			tri.b.x = origin.x + (float) Math.cos(angle1) * radius;
			tri.b.y = origin.y + (float) Math.sin(angle1) * radius;
			tri.c.x = origin.x + (float) Math.cos(angle2) * radius;
			tri.c.y = origin.y + (float) Math.sin(angle2) * radius;

			tri.sync();
			circleTris.add(tri);
		}
	}

	public void clear() {
		litArea.clear();
		litArea.addAll(circleTris);
	}

	public List<Triangle> occlude(Line occluder, List<Triangle> newLitArea, List<Triangle> out) {
		
		List<Triangle> litArea = this.litArea;
		{
			float x = origin.x;
			float y = origin.y;
			float r = radius;
			float dx = x - occluder.p1.x;
			float dy = y - occluder.p1.y;
			if(dx * dx + dy * dy > r * r) {
				dx = x - occluder.p2.x;
				dy = y - occluder.p2.y;
				if(dx * dx + dy * dy > r * r) {
					newLitArea.clear();
					for(int i = 0, len = litArea.size(); i < len; i++) {
						newLitArea.add(litArea.get(i));
					}

					// swap
					List<Triangle> tmp = litArea;
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
		List<Triangle> tmp = litArea;
		this.litArea = litArea = newLitArea;
		newLitArea = tmp;

		return newLitArea;
	}
}