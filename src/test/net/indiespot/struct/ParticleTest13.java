package test.net.indiespot.struct;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.Random;

import javax.swing.JOptionPane;

import net.indiespot.struct.cp.ForceUninitializedMemory;
import net.indiespot.struct.cp.Struct;
import net.indiespot.struct.cp.StructField;
import net.indiespot.struct.cp.StructType;
import net.mokyu.threading.DrawTask;
import net.mokyu.threading.GameExecutor;
import net.mokyu.threading.MultithreadedExecutor;
import net.mokyu.threading.SingleThreadExecutor;
import net.mokyu.threading.SplitTask;
import net.mokyu.threading.TaskTree;
import net.mokyu.threading.TaskTreeBuilder;

import org.lwjgl.LWJGLException;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL44.*;

/**
 * Old LWJGL MappedObject version.
 * 
 */

public class ParticleTest13 {

	private static final int WIDTH = 1600, HEIGHT = 900;
	private static final int NUM_COPY_VBOS = 3;

	private static final float spawnX = WIDTH / 2f, spawnY = HEIGHT * 3 / 4f;
	private static final float airResistance = 0.99f;
	private static final float launchX = 35, launchY = 20;
	private static final float gravity = 0.05f;
	private static int NUM_PARTICLES = 130_000;
	private static final int lifeTime = 200;
	private static final float particlePointSize = 1;
	private static final int defaultThreads = 8;

	private boolean keyPressed = false;
	private GameExecutor executor;
	private TaskTree taskTree;
	private int threads;
	private int particlesPerThread;

	private int persistentVBO;
	private ByteBuffer persistentBuffer;
	private Particle[] particleStructArray;

	private int[] copyVBOs;
	private int currentCopyVBO;

	private ParticleSubtask[] particleSubtasks;
	private Random seedGenerator = new Random();

	public static void main(String[] args) {
		new ParticleTest13().gameloop();
	}

	static void releaseInit() {
		System.setProperty("org.lwjgl.librarypath", new File("").getAbsolutePath() + "\\natives");

		while (true) {
			try {
				String s = JOptionPane.showInputDialog("Hello! How many particles do you want to simulate?", 1000000);
				if(s == null) {
					System.exit(0);
				}
				int i = Integer.parseInt(s);
				if(i <= 0) {
					JOptionPane.showMessageDialog(null, "Over 0 as in over 0. Please try again.");
				}
				NUM_PARTICLES = i;
				break;
			}
			catch (Exception e) {
				JOptionPane.showMessageDialog(null, "Error parsing int, please try again.");
			}
		}

		JOptionPane.showMessageDialog(null, "Okay. To change the number of threads, use the number keys. 0 is a\nspecial one that sets the number of threads to 16.");
	}

	public ParticleTest13() {
		new Thread() {
			{
				setDaemon(true);
			}

			public void run() {
				while (true) {
					try {
						Thread.sleep(Long.MAX_VALUE);
					}
					catch (InterruptedException ex) {
						ex.printStackTrace();
					}
				}
			}
		}.start();

		try {
			Display.setDisplayModeAndFullscreen(Display.getDesktopDisplayMode());
			//Display.setDisplayMode(new DisplayMode(WIDTH, HEIGHT));
			Display.create();
			Display.setVSyncEnabled(false);
			Keyboard.create();
		}
		catch (LWJGLException ex) {
			ex.printStackTrace();
		}

		glOrtho(0, WIDTH, HEIGHT, 0, -1, 1);

		init(defaultThreads);
	}

	private void init(int numThreads) {
		threads = numThreads;
		particlesPerThread = NUM_PARTICLES / numThreads;

		if(persistentVBO != 0) {
			glDeleteBuffers(persistentVBO);
			persistentBuffer = null;
		}

		if(executor != null && executor instanceof MultithreadedExecutor) {
			((MultithreadedExecutor) executor).close();
		}

		if(threads == 1) {
			executor = new SingleThreadExecutor();
		}
		else {
			executor = new MultithreadedExecutor(numThreads);
		}
		TaskTreeBuilder builder = new TaskTreeBuilder();
		builder.addTask(new UpdateTask());
		builder.addTask(new RenderTask());
		taskTree = builder.build();

		persistentVBO = glGenBuffers();
		glBindBuffer(GL_ARRAY_BUFFER, persistentVBO);
		//glBufferStorage(GL_ARRAY_BUFFER, PARTICLE_BYTE_SIZE * NUM_PARTICLES, GL_MAP_WRITE_BIT | GL_MAP_PERSISTENT_BIT | GL_MAP_COHERENT_BIT);
		//persistentBuffer = glMapBufferRange(GL_ARRAY_BUFFER, 0, PARTICLE_BYTE_SIZE * NUM_PARTICLES, GL_MAP_WRITE_BIT | GL_MAP_PERSISTENT_BIT | GL_MAP_COHERENT_BIT, null);
		persistentBuffer = glMapBufferRange(GL_ARRAY_BUFFER, 0, PARTICLE_BYTE_SIZE * NUM_PARTICLES, GL_MAP_WRITE_BIT, null);

		System.out.println(Particle.class);
		particleStructArray = Struct.map(Particle.class, persistentBuffer);

		Random random = new Random();
		for(int i = 0; i < NUM_PARTICLES; i++) {

			float x = spawnX;
			float y = spawnY;
			float vx = (random.nextFloat() - 0.5f) * launchX;
			float vy = -random.nextFloat() * launchY;
			byte r = (byte) random.nextInt(256);
			byte g = (byte) random.nextInt(256);
			byte b = (byte) random.nextInt(256);
			float life = random.nextFloat() * (lifeTime / 60f);
			particleStructArray[i].init(x, y, vx, vy, r, g, b, life);
		}

		particleSubtasks = new ParticleSubtask[threads];
		for(int i = 0; i < threads; i++) {
			particleSubtasks[i] = new ParticleSubtask(i * particlesPerThread, (i + 1) * particlesPerThread);
		}

		copyVBOs = new int[NUM_COPY_VBOS];
		for(int i = 0; i < NUM_COPY_VBOS; i++) {
			int vbo = glGenBuffers();
			glBindBuffer(GL_ARRAY_BUFFER, vbo);
			glBufferStorage(GL_ARRAY_BUFFER, PARTICLE_BYTE_SIZE * NUM_PARTICLES, 0);
			copyVBOs[i] = vbo;
		}
		currentCopyVBO = 0;
	}

	public void gameloop() {

		//Timer timer = new Timer();
		//FPSCalculator fps = new FPSCalculator();

		while (!Display.isCloseRequested()) {

			executor.run(taskTree);

			/*
			 * fps.update(); Timer.tick(); if (timer.getTime() >= 1) {
			 * timer.reset(); System.out.println("\n//////////////////////");
			 * System.out.println("FPS: " + fps.getCurrentFPS());
			 * System.out.println("Particles: " + NUM_PARTICLES);
			 * System.out.println("Threads: " + threads + " / " +
			 * Runtime.getRuntime().availableProcessors()); }
			 */
		}
	}

	private class UpdateTask extends SplitTask {

		public UpdateTask() {
			super(0, 0, threads);
		}

		@Override
		protected void runSubtask(int subtask) {
			particleSubtasks[subtask].update();
		}

		@Override
		public void finish() {
		}
	}

	public class RenderTask extends DrawTask {

		public RenderTask() {
			super(1, 0);
			addRequiredTask(0);
		}

		@Override
		protected void run() {

			glBindBuffer(GL_ARRAY_BUFFER, persistentVBO);
			//glFlushMappedBufferRange(GL_ARRAY_BUFFER, 0, NUM_PARTICLES * PARTICLE_BYTE_SIZE);

			/*
			 * glBindBuffer(GL_COPY_READ_BUFFER, persistentVBO);
			 * glBindBuffer(GL_COPY_WRITE_BUFFER, copyVBOs[currentCopyVBO]);
			 * glCopyBufferSubData(GL_COPY_READ_BUFFER, GL_COPY_WRITE_BUFFER, 0,
			 * 0, NUM_PARTICLES * PARTICLE_BYTE_SIZE);
			 */

			glClearColor(0, 0, 0, 0);
			glClear(GL_COLOR_BUFFER_BIT);

			glEnable(GL_POINT_SMOOTH);
			glEnable(GL_BLEND);
			glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
			//glBlendFunc(GL_SRC_ALPHA, GL_ONE);

			glPointSize(particlePointSize);

			//glBindBuffer(GL_ARRAY_BUFFER, copyVBOs[currentCopyVBO]);
			glBindBuffer(GL_ARRAY_BUFFER, persistentVBO);
			glEnableClientState(GL_VERTEX_ARRAY);
			glEnableClientState(GL_COLOR_ARRAY);

			glVertexPointer(2, GL_FLOAT, PARTICLE_BYTE_SIZE, 0);
			glColorPointer(4, GL_UNSIGNED_BYTE, PARTICLE_BYTE_SIZE, 24);

			glDrawArrays(GL_POINTS, 0, NUM_PARTICLES);

			Display.update();
			//glFinish();

			currentCopyVBO = (currentCopyVBO + 1) % NUM_COPY_VBOS;

			boolean newKey = false;
			for(int i = 0; i < 10; i++) {
				int keyToCheck = Keyboard.KEY_1 + i;
				if(i == 9) {
					keyToCheck = Keyboard.KEY_0;
					i = 15;
				}
				if(Keyboard.isKeyDown(keyToCheck)) {
					newKey = true;
					if(!keyPressed) {
						init(i + 1);
						System.out.println("NUMBER OF THREADS CHANGED TO " + (i + 1));
						keyPressed = true;
					}
					break;
				}
			}
			if(!newKey) {
				keyPressed = false;
			}
		}
	}

	private class ParticleSubtask {

		private Random random;
		private int start, end;

		public ParticleSubtask(int start, int end) {

			this.start = start;
			this.end = end;

			random = new Random(seedGenerator.nextLong());
		}

		public void update() {
			for(int i = start; i < end; i++) {

				Particle p = particleStructArray[i];

				//System.out.println(p.toString());
				//System.out.println(p);

				if(p.isDead()) {
					float x = spawnX;
					float y = spawnY;
					float vx = (random.nextFloat() - 0.5f) * launchX;
					float vy = -random.nextFloat() * launchY;
					byte r = (byte) random.nextInt(256);
					byte g = (byte) random.nextInt(256);
					byte b = (byte) random.nextInt(256);
					float life = random.nextFloat() * lifeTime / 60f;
					p.init(x, y, vx, vy, r, g, b, life);
				}

				p.update();
				/*
				 * System.out.println(p.toString()); System.out.println(p);
				 * System.out.println();
				 */
			}
		}
	}

	private static final int PARTICLE_BYTE_SIZE = 28;

	@StructType(sizeof = PARTICLE_BYTE_SIZE)
	@ForceUninitializedMemory
	public static class Particle {

		@StructField(offset = 0) private float x;
		@StructField(offset = 4) private float y;

		@StructField(offset = 8) private float vx;
		@StructField(offset = 12) private float vy;

		@StructField(offset = 16) private float lifeLeft;
		@StructField(offset = 20) private float totalLife;

		@StructField(offset = 24) private byte r;
		@StructField(offset = 25) private byte g;
		@StructField(offset = 26) private byte b;
		@StructField(offset = 27) private byte a;

		public void init(float x, float y, float vx, float vy, byte r, byte g, byte b, float life) {
			this.x = x;
			this.y = y;

			this.r = r;
			this.g = g;
			this.b = b;

			this.vx = vx;
			this.vy = vy;

			totalLife = life;
			lifeLeft = life;
		}

		public void update() {
			vy += gravity;

			vx *= airResistance;
			vy *= airResistance;

			x += vx;
			y += vy;

			if((vx < 0 && x < 0) || (vx > 0 && x > WIDTH)) {
				vx = -vx;
			}
			if((vy < 0 && y < 0) || (vy > 0 && y > HEIGHT)) {
				vy = -vy;
			}

			lifeLeft = Math.max(0, lifeLeft - 1 / 60f);

			a = (byte) (255 * lifeLeft / totalLife);
		}

		public boolean isDead() {
			return lifeLeft <= 0;
		}

		@Override
		public String toString() {
			return "Particle[(" + x + ", " + y + "), (" + vx + ", " + vy + "), (" + r + ", " + g + ", " + b + ", " + a + "), " + lifeLeft + "/" + totalLife + "]";
		}
	}
}