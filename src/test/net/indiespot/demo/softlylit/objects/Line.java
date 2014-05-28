package test.net.indiespot.demo.softlylit.objects;

public class Line {

	public Point p1, p2;

	public Line() {
		p1 = new Point();
		p2 = new Point();
	}

	public float side(float x, float y) {
		return (p2.x - p1.x) * (y - p1.y) - (p2.y - p1.y) * (x - p1.x);
	}

	public static boolean intersectSegment(Line a, Line b, Point intersection) {
		float x1 = a.p1.x, y1 = a.p1.y, x2 = a.p2.x, y2 = a.p2.y;
		float x3 = b.p1.x, y3 = b.p1.y, x4 = b.p2.x, y4 = b.p2.y;
		float x1_x2 = x1 - x2;
		float x3_x4 = x3 - x4;
		float y1_y2 = y1 - y2;
		float y3_y4 = y3 - y4;

		float det = x1_x2 * y3_y4 - y1_y2 * x3_x4;
		if(det == 0.0f) {
			return false;
		}

		float cx, cy, dx, dy;
		float x1y2_y1x2 = (x1 * y2 - y1 * x2);
		float x3y4_y3x4 = (x3 * y4 - y3 * x4);
		float px = (x1y2_y1x2 * x3_x4 - x1_x2 * x3y4_y3x4) / det;
		float py = (x1y2_y1x2 * y3_y4 - y1_y2 * x3y4_y3x4) / det;

		dx = px - (cx = (x1 + x2) * 0.5f);
		dy = py - (cy = (y1 + y2) * 0.5f);
		if((dx * dx) + (dy * dy) < (x1 - cx) * (x1 - cx) + (y1 - cy) * (y1 - cy)) {
			dx = px - (cx = (x3 + x4) * 0.5f);
			dy = py - (cy = (y3 + y4) * 0.5f);
			if((dx * dx) + (dy * dy) < (x3 - cx) * (x3 - cx) + (y3 - cy) * (y3 - cy)) {
				intersection.x = px;
				intersection.y = py;
				return true;
			}
		}

		return false;
	}

	public static boolean intersectExtended(Line a, Line b, Point intersection) {
		float x1 = a.p1.x, y1 = a.p1.y, x2 = a.p2.x, y2 = a.p2.y;
		float x3 = b.p1.x, y3 = b.p1.y, x4 = b.p2.x, y4 = b.p2.y;
		float x1_x2 = x1 - x2;
		float x3_x4 = x3 - x4;
		float y1_y2 = y1 - y2;
		float y3_y4 = y3 - y4;

		float det = x1_x2 * y3_y4 - y1_y2 * x3_x4;
		if(det == 0.0f) {
			return false;
		}

		float x1y2_y1x2 = (x1 * y2 - y1 * x2);
		float x3y4_y3x4 = (x3 * y4 - y3 * x4);
		float px = (x1y2_y1x2 * x3_x4 - x1_x2 * x3y4_y3x4) / det;
		float py = (x1y2_y1x2 * y3_y4 - y1_y2 * x3y4_y3x4) / det;

		intersection.x = px;
		intersection.y = py;
		return true;
	}
}