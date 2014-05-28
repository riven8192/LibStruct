package test.net.indiespot.demo.softlylit.structs;

import net.indiespot.struct.cp.Struct;
import net.indiespot.struct.cp.StructType;
import net.indiespot.struct.cp.TakeStruct;

@StructType(sizeof = 72)
public class Triangle {
	@TakeStruct
	public Point a() {
		return Struct.view(this, Point.class, 0);
	}

	@TakeStruct
	public Point b() {
		return Struct.view(this, Point.class, 8);
	}

	@TakeStruct
	public Point c() {
		return Struct.view(this, Point.class, 16);
	}

	@TakeStruct
	public Line ab() {
		return Struct.view(this, Line.class, 24);
	}

	@TakeStruct
	public Line bc() {
		return Struct.view(this, Line.class, 40);
	}

	@TakeStruct
	public Line ca() {
		return Struct.view(this, Line.class, 56);
	}

	private Triangle() {
		// hide
	}
	
	@TakeStruct
	public Triangle load(Point a, Point b, Point c){
		a().load(a);
		b().load(b);
		c().load(c);
		return this;
	}

	public void sync() {
		ab().load(a(), b());
		bc().load(b(), c());
		ca().load(c(), a());
	}
}