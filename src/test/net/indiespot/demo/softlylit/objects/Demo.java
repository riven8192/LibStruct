package test.net.indiespot.demo.softlylit.objects;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.PixelFormat;

import static org.lwjgl.opengl.GL11.*;

public class Demo {

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
			area.origin = new Point();
			area.origin.x = RNDM.nextFloat() * 512;
			area.origin.y = RNDM.nextFloat() * 512;
			area.radius = 64.0f;
			area.triangleCount = 13;
			area.sync();

			areas.add(area);
		}

		List<Triangle> tmp = new ArrayList<>();
		List<Triangle> out = new ArrayList<>();
		List<Line> occluders = new ArrayList<>();

		final float diffuse = (float) 1 / 0xff;
		final float ambient = 0.25f;

		Display.setDisplayMode(new DisplayMode(512, 512));
		Display.setTitle("Softly Lit - with objects");
		Display.create(new PixelFormat(8, 8, 8, 4));
		while (!Display.isCloseRequested()) {
			glClearColor(0, 0, 0, 1);
			glClear(GL_COLOR_BUFFER_BIT);

			glMatrixMode(GL_PROJECTION);
			glLoadIdentity();
			glOrtho(0, 512, 512, 0, -1, +1);

			glMatrixMode(GL_MODELVIEW);
			glLoadIdentity();

			glEnable(GL_BLEND);
			glBlendFunc(GL_ONE, GL_ONE);

			tmp.clear();
			out.clear();
			occluders.clear();

			for(Faller faller : fallers) {
				occluders.add(faller.tick());
			}

			glBegin(GL_TRIANGLES);

			glColor3f(diffuse, diffuse, diffuse);
			long took = 0;
			for(LightArea area : areas) {
				area.sync();
				area.clear();

				took -= System.nanoTime();
				for(Line occluder : occluders) {
					tmp = area.occlude(occluder, tmp, out);
				}
				took += System.nanoTime();

				for(Triangle tri : area.litArea) {
					glVertex2f(tri.a.x, tri.a.y);
					glVertex2f(tri.b.x, tri.b.y);
					glVertex2f(tri.c.x, tri.c.y);
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

			Display.update();
			Display.sync(60);
		}
		Display.destroy();
	}

	public static void occlude(Triangle litArea, Line occluder, List<Triangle> out) {

		out.clear();

		Point abIntersection = new Point();
		Point bcIntersection = new Point();
		Point caIntersection = new Point();

		boolean ab = Line.intersectSegment(litArea.ab, occluder, abIntersection);
		boolean bc = Line.intersectSegment(litArea.bc, occluder, bcIntersection);
		boolean ca = Line.intersectSegment(litArea.ca, occluder, caIntersection);
		int bits = ((ab ? 1 : 0) << 2) | ((bc ? 1 : 0) << 1) | ((ca ? 1 : 0) << 0);

		boolean abSide = litArea.ab.side(occluder.p1.x, occluder.p1.y) > 0.0f;
		boolean bcSide = litArea.bc.side(occluder.p1.x, occluder.p1.y) > 0.0f;
		boolean caSide = litArea.ca.side(occluder.p1.x, occluder.p1.y) > 0.0f;
		boolean inside = abSide == bcSide && bcSide == caSide;

		switch (bits) {
		case 0b000: {
			if(inside) {
				Line l1 = new Line();
				l1.p1.load(litArea.a);
				l1.p2.load(occluder.p1);

				Line l2 = new Line();
				l2.p1.load(litArea.a);
				l2.p2.load(occluder.p2);

				Point intersectionBCl = new Point();
				Point intersectionBCr = new Point();
				Line.intersectExtended(litArea.bc, l1, intersectionBCl);
				Line.intersectExtended(litArea.bc, l2, intersectionBCr);

				boolean swap = false;
				swap ^= litArea.ab.side(occluder.p1.x, occluder.p1.y) < 0.0f;
				swap ^= occluder.side(litArea.a.x, litArea.a.y) < 0.0f;
				if(swap) {
					Point tmp = intersectionBCl;
					intersectionBCl = intersectionBCr;
					intersectionBCr = tmp;
				}

				out.add(new Triangle().load(litArea.a, litArea.b, intersectionBCl));
				out.add(new Triangle().load(litArea.a, occluder.p1, occluder.p2));
				out.add(new Triangle().load(litArea.a, intersectionBCr, litArea.c));
			}
			else {
				out.add(litArea);
			}
			break;
		}

		case 0b001: {
			Line center = new Line();
			center.p1.load(litArea.a);
			center.p2.load(inside ? occluder.p1 : occluder.p2);
			Line.intersectExtended(litArea.bc, center, bcIntersection);

			out.add(new Triangle().load(litArea.a, litArea.b, bcIntersection));
			out.add(new Triangle().load(litArea.a, center.p2, caIntersection));
			break;
		}

		case 0b010: {
			Line center = new Line();
			center.p1.load(litArea.a);
			center.p2.load(inside ? occluder.p1 : occluder.p2);
			Point bcIntersection2 = new Point();
			Line.intersectExtended(litArea.bc, center, bcIntersection2);

			boolean swap = false;
			swap ^= occluder.side(litArea.a.x, litArea.a.y) < 0.0f;
			swap ^= occluder.side(litArea.b.x, litArea.b.y) > 0.0f;
			if(swap) {
				out.add(new Triangle().load(litArea.a, litArea.b, bcIntersection));
				out.add(new Triangle().load(litArea.a, bcIntersection, center.p2));
				out.add(new Triangle().load(litArea.a, bcIntersection2, litArea.c));
			}
			else {
				out.add(new Triangle().load(litArea.a, litArea.b, bcIntersection2));
				out.add(new Triangle().load(litArea.a, bcIntersection, litArea.c));
				out.add(new Triangle().load(litArea.a, center.p2, bcIntersection));
			}
			break;
		}

		case 0b011: {
			out.add(new Triangle().load(litArea.a, bcIntersection, caIntersection));
			out.add(new Triangle().load(litArea.a, litArea.b, bcIntersection));
			break;
		}

		case 0b100: {
			Line center = new Line();
			center.p1.load(litArea.a);
			center.p2.load(inside ? occluder.p1 : occluder.p2);
			Line.intersectExtended(litArea.bc, center, bcIntersection);

			out.add(new Triangle().load(litArea.a, abIntersection, center.p2));
			out.add(new Triangle().load(litArea.a, bcIntersection, litArea.c));
			break;
		}

		case 0b101: {
			out.add(new Triangle().load(litArea.a, abIntersection, caIntersection));
			break;
		}

		case 0b110: {
			out.add(new Triangle().load(litArea.a, abIntersection, bcIntersection));
			out.add(new Triangle().load(litArea.a, bcIntersection, litArea.c));
			break;
		}

		default:
			//System.err.println("occluder cannot intersect all sides of triangle");
		}
	}
}