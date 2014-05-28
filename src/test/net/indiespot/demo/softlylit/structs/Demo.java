package test.net.indiespot.demo.softlylit.structs;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.indiespot.struct.cp.Struct;
import net.indiespot.struct.runtime.StructAllocationStack;

import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.PixelFormat;

import test.net.indiespot.demo.softlylit.structs.support.LineList;
import test.net.indiespot.demo.softlylit.structs.support.TriangleList;
import static org.lwjgl.opengl.GL11.*;

public class Demo {

	public static StructAllocationStack STACK = Struct.createStructAllocationStack(64 * 1024 * 1024);

	public static final Random RNDM = new Random(236);
	private static List<Long> GC_DURATIONS = new ArrayList<Long>();

	public static void main(String[] args) throws Exception {
		{
			Runnable task = new Runnable() {
				@Override
				public void run() {
					List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();

					long[] dur = new long[gcBeans.size()];

					while (true) {
						for(int i = 0; i < dur.length; i++) {
							long was = dur[i];
							long now = dur[i] = gcBeans.get(i).getCollectionTime();
							if(was < now) {
								synchronized (GC_DURATIONS) {
									GC_DURATIONS.add(Long.valueOf(now - was));
								}
							}
						}
					}
				}
			};

			Thread t = new Thread(task);
			t.setName("GC Pause Monitor");
			t.setDaemon(true);
			t.start();
		}

		final List<Faller> fallers = new ArrayList<>();
		for(int i = 0; i < 64; i++) {
			Faller faller = new Faller();
			faller.init();
			fallers.add(faller);
		}

		final int lightDim = 64;

		final List<LightArea> areas = new ArrayList<>();
		for(int i = 0; i < lightDim * lightDim; i++) {
			LightArea area = new LightArea();
			area.origin = Struct.stackAlloc(STACK, Point.class);
			area.origin.x = RNDM.nextFloat() * 512;
			area.origin.y = RNDM.nextFloat() * 512;
			area.radius = 64.0f;
			area.triangleCount = 13;
			area.sync();

			areas.add(area);
		}

		STACK.save();

		TriangleList tmp = new TriangleList();
		TriangleList out = new TriangleList();
		LineList occluders = new LineList();

		final float diffuse = (float) 1 / 0xff;
		final float ambient = 0.25f;

		Display.setDisplayMode(new DisplayMode(512, 512));
		Display.setTitle("Softly Lit - with structs");
		Display.create(new PixelFormat(8, 8, 8, 4));
		while (!Display.isCloseRequested()) {
			STACK.save();

			glClearColor(0, 0, 0, 1);
			glClear(GL_COLOR_BUFFER_BIT);

			glMatrixMode(GL_PROJECTION);
			glLoadIdentity();
			glOrtho(0, 512, 512, 0, -1, +1);

			glMatrixMode(GL_MODELVIEW);
			glLoadIdentity();

			tmp.clear();
			out.clear();
			occluders.clear();

			for(Faller faller : fallers) {
				occluders.add(faller.tick());
			}

			glEnable(GL_BLEND);
			glBlendFunc(GL_ONE, GL_ONE);

			glBegin(GL_TRIANGLES);

			glColor3f(diffuse, diffuse, diffuse);
			long took = 0;
			for(LightArea area : areas) {
				area.sync();
				area.clear();

				took -= System.nanoTime();
				for(int i = 0, len = occluders.size(); i < len; i++) {
					tmp = area.occlude(occluders.get(i), tmp, out);
				}
				took += System.nanoTime();

				for(int i = 0, len = area.litArea.size(); i < len; i++) {
					Triangle tri = area.litArea.get(i);
					glVertex2f(tri.a().x, tri.a().y);
					glVertex2f(tri.b().x, tri.b().y);
					glVertex2f(tri.c().x, tri.c().y);
				}
			}

			String msg = "SoftlyLit - structs - ";
			msg += "calc took: " + (took / 1_000_000L) + "ms";
			msg += ", GC: [";
			synchronized (GC_DURATIONS) {
				msg += "#" + GC_DURATIONS.size() + ": ";
				for(int i = GC_DURATIONS.size() - 1; i >= Math.max(0, GC_DURATIONS.size() - 5); i--) {
					msg += GC_DURATIONS.get(i).longValue() + "ms,";
				}
			}
			msg += "-]";
			Display.setTitle(msg);

			glColor3f(ambient, ambient, ambient);
			glVertex2f(0 * 512, 0 * 512);
			glVertex2f(1 * 512, 0 * 512);
			glVertex2f(0 * 512, 1 * 512);
			glVertex2f(1 * 512, 0 * 512);
			glVertex2f(0 * 512, 1 * 512);
			glVertex2f(1 * 512, 1 * 512);

			glEnd();
			glDisable(GL_BLEND);

			Display.update();
			Display.sync(60);

			STACK.restore();
		}
		Display.destroy();
	}

	public static void occlude(Triangle litArea, Line occluder, TriangleList out) {

		out.clear();

		Point op1 = occluder.p1();
		Point op2 = occluder.p2();

		Point litA = litArea.a();
		Point litB = litArea.b();
		Point litC = litArea.c();

		Line lAB = litArea.ab();
		Line lBC = litArea.bc();
		Line lCA = litArea.ca();

		Point abIntersection = Struct.stackAlloc(STACK, Point.class);
		Point bcIntersection = Struct.stackAlloc(STACK, Point.class);
		Point caIntersection = Struct.stackAlloc(STACK, Point.class);

		boolean ab = Line.intersectSegment(lAB, occluder, abIntersection);
		boolean bc = Line.intersectSegment(lBC, occluder, bcIntersection);
		boolean ca = Line.intersectSegment(lCA, occluder, caIntersection);
		int bits = ((ab ? 1 : 0) << 2) | ((bc ? 1 : 0) << 1) | ((ca ? 1 : 0) << 0);

		boolean abSide = lAB.side(op1) > 0.0f;
		boolean bcSide = lBC.side(op1) > 0.0f;
		boolean caSide = lCA.side(op1) > 0.0f;
		boolean inside = abSide == bcSide && bcSide == caSide;

		if(bits == 0b000) {
			if(inside) {
				Line l1 = Struct.stackAlloc(STACK, Line.class).load(litA, op1);
				Line l2 = Struct.stackAlloc(STACK, Line.class).load(litA, op2);

				Point intersectionBCl = Struct.stackAlloc(STACK, Point.class);
				Point intersectionBCr = Struct.stackAlloc(STACK, Point.class);
				Line.intersectExtended(lBC, l1, intersectionBCl);
				Line.intersectExtended(lBC, l2, intersectionBCr);

				boolean swap = false;
				swap ^= lAB.side(op1) < 0.0f;
				swap ^= occluder.side(litA) < 0.0f;
				if(swap) {
					Point tmp = intersectionBCl;
					intersectionBCl = intersectionBCr;
					intersectionBCr = tmp;
				}

				out.add(Struct.stackAlloc(STACK, Triangle.class).load(litA, litB, intersectionBCl));
				out.add(Struct.stackAlloc(STACK, Triangle.class).load(litA, op1, op2));
				out.add(Struct.stackAlloc(STACK, Triangle.class).load(litA, intersectionBCr, litC));
			}
			else {
				out.add(litArea);
			}
		}
		else if(bits == 0b101) {
			out.add(Struct.stackAlloc(STACK, Triangle.class).load(litA, abIntersection, caIntersection));
		}
		else if(bits == 0b100) {
			Line center = Struct.stackAlloc(STACK, Line.class);
			center.load(litA, inside ? op1 : op2);
			Line.intersectExtended(lBC, center, bcIntersection);

			out.add(Struct.stackAlloc(STACK, Triangle.class).load(litA, abIntersection, center.p2()));
			out.add(Struct.stackAlloc(STACK, Triangle.class).load(litA, bcIntersection, litC));
		}
		else if(bits == 0b001) {
			Line center = Struct.stackAlloc(STACK, Line.class).load(litA, inside ? op1 : op2);
			Line.intersectExtended(lBC, center, bcIntersection);

			out.add(Struct.stackAlloc(STACK, Triangle.class).load(litA, litB, bcIntersection));
			out.add(Struct.stackAlloc(STACK, Triangle.class).load(litA, center.p2(), caIntersection));
		}
		else if(bits == 0b010) {
			Line center = Struct.stackAlloc(STACK, Line.class).load(litA, inside ? op1 : op2);
			Point bcIntersection2 = Struct.stackAlloc(STACK, Point.class);
			Line.intersectExtended(lBC, center, bcIntersection2);

			boolean swap = false;
			swap ^= occluder.side(litA) < 0.0f;
			swap ^= occluder.side(litB) > 0.0f;
			if(swap) {
				out.add(Struct.stackAlloc(STACK, Triangle.class).load(litA, litB, bcIntersection));
				out.add(Struct.stackAlloc(STACK, Triangle.class).load(litA, bcIntersection, center.p2()));
				out.add(Struct.stackAlloc(STACK, Triangle.class).load(litA, bcIntersection2, litC));
			}
			else {
				out.add(Struct.stackAlloc(STACK, Triangle.class).load(litA, litB, bcIntersection2));
				out.add(Struct.stackAlloc(STACK, Triangle.class).load(litA, bcIntersection, litC));
				out.add(Struct.stackAlloc(STACK, Triangle.class).load(litA, center.p2(), bcIntersection));
			}
		}
		else if(bits == 0b011) {
			out.add(Struct.stackAlloc(STACK, Triangle.class).load(litA, bcIntersection, caIntersection));
			out.add(Struct.stackAlloc(STACK, Triangle.class).load(litA, litB, bcIntersection));
		}

		else if(bits == 0b110) {
			out.add(Struct.stackAlloc(STACK, Triangle.class).load(litA, abIntersection, bcIntersection));
			out.add(Struct.stackAlloc(STACK, Triangle.class).load(litA, bcIntersection, litC));
		}
		else {
			//System.err.println("occluder cannot intersect all sides of triangle");
		}
	}
}