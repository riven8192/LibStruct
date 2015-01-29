package test.net.indiespot.demo.softlylit.objects;

public class Triangle {

	final Point a, b, c;
	final Line ab, bc, ca;

	public Triangle() {
		a = new Point();
		b = new Point();
		c = new Point();

		ab = new Line();
		bc = new Line();
		ca = new Line();
	}

	public Triangle load(Point a, Point b, Point c) {
		this.a.load(a);
		this.b.load(b);
		this.c.load(c);
		return this;
	}

	public void sync() {
		ab.p1.x = a.x;
		ab.p1.y = a.y;
		ab.p2.x = b.x;
		ab.p2.y = b.y;

		bc.p1.x = b.x;
		bc.p1.y = b.y;
		bc.p2.x = c.x;
		bc.p2.y = c.y;

		ca.p1.x = c.x;
		ca.p1.y = c.y;
		ca.p2.x = a.x;
		ca.p2.y = a.y;
	}
}