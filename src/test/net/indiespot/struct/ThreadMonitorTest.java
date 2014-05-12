package test.net.indiespot.struct;

import java.util.Random;

import net.indiespot.struct.runtime.ThreadMonitor;

public class ThreadMonitorTest {
	public static void main(String[] args) {
		ThreadMonitor.setPollInterval(100);

		ThreadMonitor.addListener(new ThreadMonitor.ThreadListener() {
			@Override
			public void onThreadStart(long threadId) {
				System.out.println("onThreadStart:" + threadId);
			}

			@Override
			public void onThreadDeath(long threadId) {
				System.out.println("onThreadDeath:" + threadId);
			}
		});

		Random rndm = new Random();
		for(int i = 0; i < 100; i++) {
			spawnWorker(rndm.nextInt(5000));
		}
	}

	private static void spawnWorker(final long delay) {
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					Thread.sleep(delay);
				}
				catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}).start();
	}
}
