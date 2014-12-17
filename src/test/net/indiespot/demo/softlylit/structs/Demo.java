package test.net.indiespot.demo.softlylit.structs;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.indiespot.struct.cp.Struct;
import net.indiespot.struct.runtime.StructAllocationStack;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ARBFragmentShader;
import org.lwjgl.opengl.ARBVertexShader;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.PixelFormat;

import test.net.indiespot.demo.softlylit.structs.support.OpenGL;
import static org.lwjgl.opengl.ARBShaderObjects.glUseProgramObjectARB;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public class Demo {

	public static StructAllocationStack STACK;

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
						for (int i = 0; i < dur.length; i++) {
							long was = dur[i];
							long now = dur[i] = gcBeans.get(i).getCollectionTime();
							if (was < now) {
								synchronized (GC_DURATIONS) {
									GC_DURATIONS.add(Long.valueOf(now - was));
								}
							}
						}

						try {
							Thread.sleep(10);
						} catch (InterruptedException exc) {
							// ignore
						}
					}
				}
			};

			Thread t = new Thread(task);
			t.setName("GC Pause Monitor");
			t.setDaemon(true);
			t.start();
		}

		final int quality = 8; // 1..8
		final int lightDim = (16 * quality);
		final float diffuse = 0.16f / (quality * quality);
		final float ambient = 0.25f;
		STACK = Struct.createStructAllocationStack((quality * quality) * 8 * 1024 * 1024);

		final List<Faller> fallers = new ArrayList<>();
		for (int i = 0; i < 64; i++) {
			Faller faller = new Faller();
			faller.init();
			fallers.add(faller);
		}

		final List<LightArea> areas = new ArrayList<>();
		for (int i = 0; i < lightDim * lightDim; i++) {
			LightArea area = new LightArea();
			area.radius = 64.0f;
			area.origin = Struct.stackAlloc(STACK, Point.class);
			area.origin.x = RNDM.nextFloat() * (512 + 2 * area.radius) - area.radius;
			area.origin.y = RNDM.nextFloat() * (512 + 2 * area.radius) - area.radius;

			area.triangleCount = 13;
			area.sync();

			areas.add(area);
		}

		STACK.save();

		TriangleList tmp = new TriangleList();
		TriangleList out = new TriangleList();
		LineList occluders = new LineList();

		Display.setDisplayMode(new DisplayMode(512, 512));
		Display.setTitle("Softly Lit - with structs");
		Display.create(new PixelFormat(8, 8, 8, 4));

		final int texDim = 512;
		int texId;
		{
			texId = glGenTextures();
			glBindTexture(GL_TEXTURE_2D, texId);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
			glTexImage2D(GL_TEXTURE_2D, 0, GL_R16F, texDim, texDim, 0, GL_RED, GL_FLOAT, BufferUtils.createFloatBuffer(texDim * texDim));
		}
		int fboId = glGenFramebuffers();
		{
			glBindFramebuffer(GL_FRAMEBUFFER, fboId);
			glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texId, 0);
			System.out.println(glGetError());
			OpenGL.checkFboStatus();
		}
		int exposureUniform;
		int program;
		{
			int vertShader = OpenGL.createShader("shaders/r16f-exposure.vert", ARBVertexShader.GL_VERTEX_SHADER_ARB);
			int fragShader = OpenGL.createShader("shaders/r16f-exposure.frag", ARBFragmentShader.GL_FRAGMENT_SHADER_ARB);
			program = OpenGL.createProgram1(vertShader, fragShader);
			glUseProgramObjectARB(program);
			exposureUniform = glGetUniformLocation(program, "exposure");
			int myTextureSampler = glGetUniformLocation(program, "myTextureSampler");
			System.out.println("texId=" + texId);
			System.out.println("exposure=" + exposureUniform);
			System.out.println("myTextureSampler=" + myTextureSampler);
			glBindTexture(GL_TEXTURE_2D, texId);
			glUniform1i(myTextureSampler, 0);
			OpenGL.createProgram2(program);
		}

		long tStart = System.nanoTime();

		PointList pl = new PointList();

		while (!Display.isCloseRequested()) {
			STACK.save();

			glBindFramebuffer(GL_FRAMEBUFFER, fboId);
			long took = 0;
			if (true) {
				glViewport(0, 0, texDim, texDim);

				glMatrixMode(GL_PROJECTION);
				glLoadIdentity();
				glOrtho(0, texDim, 0, texDim, -1, +1);

				glMatrixMode(GL_MODELVIEW);
				glLoadIdentity();

				glClearColor(ambient, 0, 0, 1);
				glClear(GL_COLOR_BUFFER_BIT);

				glEnable(GL_BLEND);
				glBlendFunc(GL_ONE, GL_ONE);

				glColor3f(diffuse, 0, 0);

				Random rndm = new Random();

				glBegin(GL_TRIANGLES);
				{
					tmp.clear();
					out.clear();
					occluders.clear();

					for (Faller faller : fallers) {
						occluders.add(faller.tick());
					}

					for (LightArea area : areas) {
						area.sync();
						area.clear();

						took -= System.nanoTime();
						for (int i = 0, len = occluders.size(); i < len; i++) {
							final Line occluder = occluders.get(i);
							float dx = rndm.nextFloat() - 0.5f;
							float dy = rndm.nextFloat() - 0.5f;

							occluder.p1.add(dx, dy);
							occluder.p2.add(dx, dy);

							tmp = area.occlude(occluder, tmp, out);

							occluder.p1.add(-dx, -dy);
							occluder.p2.add(-dx, -dy);
						}
						took += System.nanoTime();

						for (int i = 0, len = area.litArea.size(); i < len; i++) {
							Triangle tri = area.litArea.get(i);
							glVertex2f(tri.a.x, tri.a.y);
							glVertex2f(tri.b.x, tri.b.y);
							glVertex2f(tri.c.x, tri.c.y);
						}
					}
				}

				glEnd();
				glDisable(GL_BLEND);
			}
			glBindFramebuffer(GL_FRAMEBUFFER, 0);

			glClearColor(0, 0, 0, 1);
			glClear(GL_COLOR_BUFFER_BIT);

			glMatrixMode(GL_PROJECTION);
			glLoadIdentity();
			glOrtho(0, 512, 512, 0, -1, +1);

			glMatrixMode(GL_MODELVIEW);
			glLoadIdentity();

			{
				long tNow = System.nanoTime();
				double tElapsed = (tNow - tStart) / 1_000_000_000.0;

				float exposure = 0.5f + (float) Math.abs(Math.sin(tElapsed * 0.5));
				glUseProgram(program);
				glUniform1f(exposureUniform, exposure);
				glEnable(GL_TEXTURE_2D);
				glBindTexture(GL_TEXTURE_2D, texId);
				glBegin(GL_QUADS);
				glTexCoord2f(0, 0);
				glVertex2f(0 * texDim, 0 * texDim);
				glTexCoord2f(1, 0);
				glVertex2f(1 * texDim, 0 * texDim);
				glTexCoord2f(1, 1);
				glVertex2f(1 * texDim, 1 * texDim);
				glTexCoord2f(0, 1);
				glVertex2f(0 * texDim, 1 * texDim);
				glEnd();
				glDisable(GL_TEXTURE_2D);
				glUseProgram(0);
			}

			String msg = "SoftlyLit - structs - ";
			msg += "calc took: " + (took / 1_000_000L) + "ms";
			msg += ", GC: [";
			synchronized (GC_DURATIONS) {
				msg += "#" + GC_DURATIONS.size() + ": ";
				for (int i = GC_DURATIONS.size() - 1; i >= Math.max(0, GC_DURATIONS.size() - 5); i--) {
					msg += GC_DURATIONS.get(i).longValue() + "ms,";
				}
			}
			msg += "-]";
			Display.setTitle(msg);

			Display.update();
			Display.sync(60);

			STACK.restore();
		}
		Display.destroy();
	}

	public static void occlude(Triangle litArea, Line occluder, TriangleList out) {

		out.clear();

		Point op1 = occluder.p1;
		Point op2 = occluder.p2;

		Point litA = litArea.a;
		Point litB = litArea.b;
		Point litC = litArea.c;

		Line lAB = litArea.ab;
		Line lBC = litArea.bc;
		Line lCA = litArea.ca;

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

		switch (bits) {
			case 0b000: {
				if (inside) {
					Line l1 = Struct.stackAlloc(STACK, Line.class).load(litA, op1);
					Line l2 = Struct.stackAlloc(STACK, Line.class).load(litA, op2);

					Point intersectionBCl = Struct.stackAlloc(STACK, Point.class);
					Point intersectionBCr = Struct.stackAlloc(STACK, Point.class);
					Line.intersectExtended(lBC, l1, intersectionBCl);
					Line.intersectExtended(lBC, l2, intersectionBCr);

					boolean swap = false;
					swap ^= lAB.side(op1) < 0.0f;
					swap ^= occluder.side(litA) < 0.0f;
					if (swap) {
						Point tmp = intersectionBCl;
						intersectionBCl = intersectionBCr;
						intersectionBCr = tmp;
					}

					out.add(Struct.stackAlloc(STACK, Triangle.class).load(litA, litB, intersectionBCl));
					out.add(Struct.stackAlloc(STACK, Triangle.class).load(litA, op1, op2));
					out.add(Struct.stackAlloc(STACK, Triangle.class).load(litA, intersectionBCr, litC));
				} else {
					out.add(litArea);
				}
				break;
			}
			case 0b101: {
				out.add(Struct.stackAlloc(STACK, Triangle.class).load(litA, abIntersection, caIntersection));
				break;
			}
			case 0b100: {
				Line center = Struct.stackAlloc(STACK, Line.class);
				center.load(litA, inside ? op1 : op2);
				Line.intersectExtended(lBC, center, bcIntersection);

				out.add(Struct.stackAlloc(STACK, Triangle.class).load(litA, abIntersection, center.p2));
				out.add(Struct.stackAlloc(STACK, Triangle.class).load(litA, bcIntersection, litC));
				break;
			}
			case 0b001: {
				Line center = Struct.stackAlloc(STACK, Line.class).load(litA, inside ? op1 : op2);
				Line.intersectExtended(lBC, center, bcIntersection);

				out.add(Struct.stackAlloc(STACK, Triangle.class).load(litA, litB, bcIntersection));
				out.add(Struct.stackAlloc(STACK, Triangle.class).load(litA, center.p2, caIntersection));
				break;
			}
			case 0b010: {
				Line center = Struct.stackAlloc(STACK, Line.class).load(litA, inside ? op1 : op2);
				Point bcIntersection2 = Struct.stackAlloc(STACK, Point.class);
				Line.intersectExtended(lBC, center, bcIntersection2);

				boolean swap = false;
				swap ^= occluder.side(litA) < 0.0f;
				swap ^= occluder.side(litB) > 0.0f;
				if (swap) {
					out.add(Struct.stackAlloc(STACK, Triangle.class).load(litA, litB, bcIntersection));
					out.add(Struct.stackAlloc(STACK, Triangle.class).load(litA, bcIntersection, center.p2));
					out.add(Struct.stackAlloc(STACK, Triangle.class).load(litA, bcIntersection2, litC));
				} else {
					out.add(Struct.stackAlloc(STACK, Triangle.class).load(litA, litB, bcIntersection2));
					out.add(Struct.stackAlloc(STACK, Triangle.class).load(litA, bcIntersection, litC));
					out.add(Struct.stackAlloc(STACK, Triangle.class).load(litA, center.p2, bcIntersection));
				}
				break;
			}
			case 0b011: {
				out.add(Struct.stackAlloc(STACK, Triangle.class).load(litA, bcIntersection, caIntersection));
				out.add(Struct.stackAlloc(STACK, Triangle.class).load(litA, litB, bcIntersection));
				break;
			}
			case 0b110: {
				out.add(Struct.stackAlloc(STACK, Triangle.class).load(litA, abIntersection, bcIntersection));
				out.add(Struct.stackAlloc(STACK, Triangle.class).load(litA, bcIntersection, litC));
				break;
			}
			default: {
				// System.err.println("occluder cannot intersect all sides of triangle");
			}
		}
	}
}