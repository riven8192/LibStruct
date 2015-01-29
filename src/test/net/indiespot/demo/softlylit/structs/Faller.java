package test.net.indiespot.demo.softlylit.structs;

import net.indiespot.struct.cp.Struct;
import net.indiespot.struct.cp.TakeStruct;

public class Faller {
	public Point origin = Struct.stackAlloc(Demo.STACK, Point.class);
	public float length;
	public float angle;
	public float dx, dy, da;

	public void init() {
		origin.x = Demo.RNDM.nextFloat() * 512;
		origin.y = Demo.RNDM.nextFloat() * 512;
		length = 20 + Demo.RNDM.nextFloat() * 40;
		angle = (Demo.RNDM.nextFloat() * (float) Math.PI * 2.0f);
		dx = (Demo.RNDM.nextFloat() - 0.5f) * 3.0f;
		dy = (Demo.RNDM.nextFloat() - 0.5f) * 3.0f;
		da = (Demo.RNDM.nextFloat() - 0.5f) * 0.2f;
	}

	@TakeStruct
	public Line tick() {
		origin.x += dx;
		origin.y += dy;
		angle += da;

		final float m = 64;
		if(origin.x < -m)
			origin.x += 512 + m * 2;
		else if(origin.x > 512 + m)
			origin.x -= 512 + m * 2;

		if(origin.y < -m)
			origin.y += 512 + m * 2;
		else if(origin.y > 512 + m)
			origin.y -= 512 + m * 2;

		Line occluder = Struct.stackAlloc(Demo.STACK, Line.class);
		occluder.p1.x = origin.x + length * (float) Math.cos(angle);
		occluder.p1.y = origin.y + length * (float) Math.sin(angle);
		occluder.p2.x = origin.x + length * (float) Math.cos(angle + Math.PI);
		occluder.p2.y = origin.y + length * (float) Math.sin(angle + Math.PI);
		return occluder;
	}
}