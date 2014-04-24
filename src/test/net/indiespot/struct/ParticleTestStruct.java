package test.net.indiespot.struct;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.Random;

import javax.swing.JOptionPane;

import net.indiespot.struct.cp.ForceUninitializedMemory;
import net.indiespot.struct.cp.StructField;
import net.indiespot.struct.cp.StructType;
import net.indiespot.struct.runtime.StructMemory;
/*import net.mokyu.threading.DrawTask;
 import net.mokyu.threading.GameExecutor;
 import net.mokyu.threading.MultithreadedExecutor;
 import net.mokyu.threading.SingleThreadExecutor;
 import net.mokyu.threading.SplitTask;
 import net.mokyu.threading.TaskTree;
 import net.mokyu.threading.TaskTreeBuilder;*/

import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL31.*;
import static org.lwjgl.opengl.GL44.*;

import org.lwjgl.util.Timer;

import test.net.indiespot.struct.StructUtil;

/**
 * Old LWJGL MappedObject version.
 * 
 */

public class ParticleTestStruct {

	private static final int WIDTH = 1600, HEIGHT = 900;
	private static final int PARTICLE_BYTE_SIZE = 28;
	private static final int NUM_COPY_VBOS = 3;

	private static final float spawnX = WIDTH / 2f, spawnY = HEIGHT * 3 / 4f;
	private static final float airResistance = 0.99f;
	private static final float launchX = 35, launchY = 20;
	private static final float gravity = 0.05f;

	private static int NUM_PARTICLES = 3000000;
	private static final int lifeTime = 200;

	private static final float particlePointSize = 1;
	private static final int defaultThreads = 8;

	private boolean keyPressed = false;
	// private GameExecutor executor;
	// private TaskTree taskTree;
	private int threads;
	private int particlesPerSubtask;

	private int persistentVBO;
	private ByteBuffer persistentBuffer;
	private Particle[] particleStructArray;

	private int[] copyVBOs;
	private int currentCopyVBO;

	private Random random;

	public static void main(String[] args) {
		new ParticleTestStruct().gameloop();
	}

	public ParticleTestStruct() {
		try {
			// Display.setDisplayModeAndFullscreen(Display.getDesktopDisplayMode());
			Display.setDisplayMode(new DisplayMode(WIDTH, HEIGHT));
			Display.create();
			Display.setVSyncEnabled(false);
			Keyboard.create();
		} catch (LWJGLException ex) {
			ex.printStackTrace();
		}

		glOrtho(0, WIDTH, HEIGHT, 0, -1, 1);

		init(defaultThreads);
	}

	private void init(int numThreads) {
		threads = numThreads;
		particlesPerSubtask = NUM_PARTICLES / numThreads;

		/*
		 * if(bufferID != 0){ glDeleteBuffers(bufferID); buffer = null; }
		 * 
		 * if (executor != null && executor instanceof MultithreadedExecutor) {
		 * ((MultithreadedExecutor) executor).close(); }
		 */

		/*
		 * if (threads == 1) { executor = new SingleThreadExecutor(); } else {
		 * executor = new MultithreadedExecutor(numThreads); } TaskTreeBuilder
		 * builder = new TaskTreeBuilder(); builder.addTask(new UpdateTask());
		 * builder.addTask(new RenderTask()); taskTree = builder.build();
		 */

		persistentVBO = glGenBuffers();
		glBindBuffer(GL_ARRAY_BUFFER, persistentVBO);
		glBufferStorage(GL_ARRAY_BUFFER, PARTICLE_BYTE_SIZE * NUM_PARTICLES, GL_MAP_WRITE_BIT | GL_MAP_PERSISTENT_BIT | GL_MAP_COHERENT_BIT);
		persistentBuffer = glMapBufferRange(GL_ARRAY_BUFFER, 0, PARTICLE_BYTE_SIZE * NUM_PARTICLES, GL_MAP_WRITE_BIT | GL_MAP_PERSISTENT_BIT | GL_MAP_COHERENT_BIT, null);
		StructMemory.alignBufferToWord(persistentBuffer);

		copyVBOs = new int[NUM_COPY_VBOS];
		for (int i = 0; i < NUM_COPY_VBOS; i++) {
			int vbo = glGenBuffers();
			glBindBuffer(GL_ARRAY_BUFFER, vbo);
			glBufferStorage(GL_ARRAY_BUFFER, PARTICLE_BYTE_SIZE * NUM_PARTICLES, 0);
			copyVBOs[i] = vbo;
		}
		currentCopyVBO = 0;

		random = new Random();

		particleStructArray = StructUtil.map(Particle.class, persistentBuffer);
		for (int i = 0; i < NUM_PARTICLES; i++) {

			float _a = spawnX;
			float _b = spawnY;
			float _c = (random.nextFloat() - 0.5f) * launchX;
			float _d = -random.nextFloat() * launchY;
			byte _e = (byte) random.nextInt(256);
			byte _f = (byte) random.nextInt(256);
			byte _g = (byte) random.nextInt(256);
			float _h = random.nextFloat() * lifeTime / 60f;
			particleStructArray[i].init(_a, _b, _c, _d, _e, _f, _g, _h);
		}
	}

	public void gameloop() {

		Timer timer = new Timer();

		while (!Display.isCloseRequested()) {

			// executor.run(taskTree);

			Timer.tick();
			if (timer.getTime() >= 1) {
				timer.reset();
				System.out.println("\n//////////////////////");
				// System.out.println("FPS: " + fps.getCurrentFPS());
				System.out.println("Particles: " + NUM_PARTICLES);
				System.out.println("Threads: " + threads + " / " + Runtime.getRuntime().availableProcessors());
			}
		}
	}

	/*
	 * private class UpdateTask extends SplitTask {
	 * 
	 * public UpdateTask() { super(0, 0, threads); }
	 * 
	 * @Override protected void runSubtask(int subtask) {
	 * 
	 * int start = particlesPerSubtask*subtask; int end =
	 * Math.min(particlesPerSubtask*(subtask+1), NUM_PARTICLES);
	 * 
	 * for(int i = start; i < end; i++){ particleStructArray[i].update(1); } }
	 * 
	 * @Override public void finish() { } }
	 * 
	 * public class RenderTask extends DrawTask {
	 * 
	 * public RenderTask() { super(1, 0); addRequiredTask(0); }
	 * 
	 * @Override protected void run() { glBindBuffer(GL_COPY_READ_BUFFER,
	 * persistentVBO); glBindBuffer(GL_COPY_WRITE_BUFFER,
	 * copyVBOs[currentCopyVBO]); glCopyBufferSubData(GL_COPY_READ_BUFFER,
	 * GL_COPY_WRITE_BUFFER, 0, 0, NUM_PARTICLES * PARTICLE_BYTE_SIZE);
	 * 
	 * 
	 * glClearColor(0, 0, 0, 0); glClear(GL_COLOR_BUFFER_BIT);
	 * 
	 * glEnable(GL_POINT_SMOOTH); glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA,
	 * GL_ONE_MINUS_SRC_ALPHA);
	 * 
	 * glPointSize(particlePointSize);
	 * 
	 * 
	 * 
	 * glBindBuffer(GL_ARRAY_BUFFER, copyVBOs[currentCopyVBO]);
	 * 
	 * glEnableClientState(GL_VERTEX_ARRAY); glEnableClientState(GL_COLOR_ARRAY);
	 * 
	 * glVertexPointer(2, GL_FLOAT, PARTICLE_BYTE_SIZE, 0); glColorPointer(4,
	 * GL_UNSIGNED_BYTE, PARTICLE_BYTE_SIZE, 16);
	 * 
	 * glDrawArrays(GL_POINTS, 0, NUM_PARTICLES);
	 * 
	 * Display.update();
	 * 
	 * currentCopyVBO = (currentCopyVBO + 1) % NUM_COPY_VBOS;
	 * 
	 * 
	 * boolean newKey = false; for (int i = 0; i < 10; i++) { int keyToCheck =
	 * Keyboard.KEY_1 + i; if (i == 9) { keyToCheck = Keyboard.KEY_0; i = 15; }
	 * if (Keyboard.isKeyDown(keyToCheck)) { newKey = true; if (!keyPressed) {
	 * init(i + 1); System.out.println("NUMBER OF THREADS CHANGED TO " + (i +
	 * 1)); keyPressed = true; } break; } } if (!newKey) { keyPressed = false; }
	 * } }
	 */

	@StructType(sizeof = PARTICLE_BYTE_SIZE)
	@ForceUninitializedMemory
	private static class Particle {

		@StructField(offset = 0)
		private float x;
		@StructField(offset = 4)
		private float y;
		@StructField(offset = 8)
		private float vx;
		@StructField(offset = 12)
		private float vy;

		@StructField(offset = 16)
		private byte r;
		@StructField(offset = 17)
		private byte g;
		@StructField(offset = 18)
		private byte b;
		@StructField(offset = 19)
		private byte a;

		@StructField(offset = 20)
		private float lifeLeft;
		@StructField(offset = 24)
		private float totalLife;

		public void init(float x, float y, float vx, float vy, byte r, byte g, byte b, float life) {
			this.x = x;
			this.y = y;
			this.vx = vx;
			this.vy = vy;

			this.r = r;
			this.g = g;
			this.b = b;

			totalLife = lifeLeft = life;
		}

		public void update(int updates) {
			for (int i = 0; i < updates; i++) {

				vy += gravity;

				vx *= airResistance;
				vy *= airResistance;

				x += vx;
				y += vy;

				a = (byte) (255 * lifeLeft / totalLife);

				if ((vx < 0 && x < 0) || (vx > 0 && x > WIDTH)) {
					vx = -vx;
				}
				if ((vy < 0 && y < 0) || (vy > 0 && y > HEIGHT)) {
					vy = -vy;
				}

				lifeLeft -= 1 / 60f;
			}
		}
	}
}