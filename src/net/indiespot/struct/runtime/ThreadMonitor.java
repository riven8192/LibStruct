package net.indiespot.struct.runtime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ThreadMonitor {
	static {
		Thread thread = new Thread(new Runnable() {
			@Override
			public void run() {
				monitor();
			}
		});

		thread.setPriority(Thread.MIN_PRIORITY);
		thread.setName("LibStruct-Thread-Monitor");
		thread.setDaemon(true);
		thread.start();
	}

	public static interface ThreadListener {
		public void onThreadStart(long threadId);

		public void onThreadDeath(long threadId);
	}

	private static final List<ThreadListener> listeners = new ArrayList<>();

	public static void addListener(ThreadListener listener) {
		if(listener == null)
			throw new NullPointerException();

		synchronized (listeners) {
			listeners.add(listener);
		}
	}

	private static volatile long poll_interval = 5000L;

	public static void setPollInterval(long millis) {
		if(millis <= 0)
			throw new IllegalArgumentException();
		poll_interval = millis;
	}

	private static void monitor() {
		Set<Long> lastThreadIds = new HashSet<>();

		while (true) {
			try {
				Thread.sleep(poll_interval);
			}
			catch (InterruptedException e) {
				// ignore
			}

			Set<Long> currThreadIds = getThreadIds();

			Set<Long> newThreads = new HashSet<>();
			newThreads.addAll(currThreadIds);
			newThreads.removeAll(lastThreadIds);

			Set<Long> deadThreads = new HashSet<>();
			deadThreads.addAll(lastThreadIds);
			deadThreads.removeAll(currThreadIds);

			synchronized (listeners) {
				for(ThreadListener listener : listeners) {
					for(Long threadId : newThreads) {
						listener.onThreadStart(threadId.longValue());
					}
				}

				for(ThreadListener listener : listeners) {
					for(Long threadId : deadThreads) {
						listener.onThreadDeath(threadId.longValue());
					}
				}
			}

			lastThreadIds = currThreadIds;
		}
	}

	private static Set<Long> getThreadIds() {
		Thread[] arr = new Thread[16];
		Set<Long> threadIds = new HashSet<>();

		int count;
		for(;;) {
			Arrays.fill(arr, null);
			count = Thread.enumerate(arr);
			if(count < arr.length)
				break;

			Arrays.fill(arr, null);
			arr = Arrays.copyOf(arr, arr.length * 2);
		}

		for(int i = 0; i < count; i++)
			if(arr[i] != null)
				threadIds.add(Long.valueOf(arr[i].getId()));
		return threadIds;
	}
}
