package net.indiespot.struct.runtime;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class StructGC {
	public static int malloc(int sizeof) {
		if(sizeof > gc_heap_size)
			throw new IllegalStateException();

		StructHeap localHeap = local_heaps.get();
		int handle = localHeap.malloc(sizeof);
		if(handle == 0) {
			// try again with a new heap
			local_heaps.set(newHeap());
			localHeap = local_heaps.get();
			handle = localHeap.malloc(sizeof);
			if(handle == 0)
				throw new IllegalStateException();
		}
		return handle;
	}

	public static void freeHandle(int handle) {
		StructHeap localHeap = local_heaps.get();
		boolean freedFromLocalHeap = localHeap.freeHandle(handle);

		if(!freedFromLocalHeap) {
			synchronized (gc_mutex) {
				gc_eden.push(handle);
			}
		}
	}

	public static void discardThreadLocal() {
		local_heaps.set(null);
	}

	public static int countGarbage() {
		int handles = 0;
		for(int i = 0; i < gc_gens.length; i++)
			handles += gc_gens[i].size;
		return handles;
	}

	private static StructHeap newHeap() {
		synchronized (gc_mutex) {
			if(!empty_heaps.isEmpty()) {
				StructHeap heap = empty_heaps.remove(empty_heaps.size() - 1);
				if(heap == null || !heap.isEmpty())
					throw new IllegalStateException();
				return heap;
			}
		}

		ByteBuffer bb = ByteBuffer.allocateDirect(gc_heap_size);
		bb.order(ByteOrder.nativeOrder());
		return new StructHeap(bb);
	}

	private static final Object gc_mutex = new Object();
	private static final IntStack gc_eden = new IntStack();
	private static final IntStack[] gc_gens = new IntStack[8];
	static {
		for(int i = 0; i < gc_gens.length; i++) {
			gc_gens[i] = (i == 0) ? gc_eden : new IntStack();
		}
	}

	private static final List<StructHeap> gc_heaps = new ArrayList<>();
	private static final List<StructHeap> empty_heaps = new ArrayList<>();
	public static FastThreadLocal<StructHeap> local_heaps = new FastThreadLocal<StructHeap>() {
		@Override
		public StructHeap initialValue() {
			return newHeap();
		}

		@Override
		public void onRelease(StructHeap heap) {
			synchronized (gc_mutex) {
				if(heap.isEmpty())
					empty_heaps.add(heap);
				else
					gc_heaps.add(heap);
			}
		}
	};

	private static volatile long gc_min_interval = 10;
	private static volatile long gc_max_interval = 2500;
	private static volatile float gc_inc_interval = 1.2f;
	private static volatile float gc_dec_interval = 0.5f;
	private static volatile long gc_max_micros = 1000;
	private static volatile int gc_heap_size = 16 * (4 * 1024); // 64K
	private static volatile long gc_max_empty_heaps = 100;
	private static volatile float gc_fail_ratio = 0.1f;

	public static void configureGarbageCollector(//
			long minIntervalMillis, //
			long maxIntervalMillis, //
			float incIntervalFactor, //
			long maxDurationMicros,//
			int heapSize,//
			int maxEmptyHeaps//
	) {
		if(minIntervalMillis < 0)
			throw new IllegalArgumentException();
		if(maxIntervalMillis < minIntervalMillis)
			throw new IllegalArgumentException();
		if(incIntervalFactor < 1.0f)
			throw new IllegalArgumentException();
		if(maxDurationMicros < 0)
			throw new IllegalArgumentException();
		if(heapSize < 0)
			throw new IllegalArgumentException();
		if(maxEmptyHeaps < 0)
			throw new IllegalArgumentException();

		gc_min_interval = minIntervalMillis;
		gc_max_interval = maxIntervalMillis;
		gc_inc_interval = incIntervalFactor;
		gc_max_micros = maxDurationMicros;
		gc_heap_size = heapSize;
		gc_max_empty_heaps = maxEmptyHeaps;
	}

	static {
		Thread thread = new Thread(new Runnable() {
			@Override
			public void run() {
				long sleep = gc_min_interval;
				int[] heapSizes = new int[gc_gens.length];

				while (true) {
					try {
						Thread.sleep(sleep);
					}
					catch (InterruptedException e) {
						// ignore
					}

					long took;
					int freed, gcHeaps, emptyHeaps;
					synchronized (gc_mutex) {
						long tBegin = System.nanoTime();
						freed = gc(tBegin);
						took = System.nanoTime() - tBegin;

						for(int i = 0; i < gc_gens.length; i++)
							heapSizes[i] = gc_gens[i].size;

						if(freed == 0)
							sleep = Math.round(sleep * gc_inc_interval);
						else
							sleep = Math.round(sleep * gc_dec_interval);

						// clamp
						sleep = Math.max(sleep, gc_min_interval);
						sleep = Math.min(sleep, gc_max_interval);

						gcHeaps = gc_heaps.size();
						emptyHeaps = empty_heaps.size();
					}

					if(freed > 0) {
						synchronized (gc_info_callbacks) {
							for(GcInfo info : gc_info_callbacks) {
								info.onGC(gcHeaps, emptyHeaps, freed, heapSizes, took);
							}
						}
					}
				}
			}
		});

		thread.setName("LibStruct-Garbage-Collector");
		thread.setDaemon(true);
		thread.setPriority(Thread.MIN_PRIORITY);
		thread.start();
	}

	private static int gc(long begin) {
		int freed = 0;
		int offset = 1;
		for(; offset < gc_gens.length; offset++)
			freed += gc(begin, gc_gens[offset - 1], gc_gens[offset]);

		if(offset == gc_gens.length) {
			// if we have time to process the oldest handles, spread them all around
			IntStack last = gc_gens[gc_gens.length - 1];
			while (!last.isEmpty())
				gc_gens[++offset % (gc_gens.length - 1)].push(last.pop());
		}

		while (empty_heaps.size() > gc_max_empty_heaps) {
			empty_heaps.remove(empty_heaps.size() - 1);
		}

		return freed;
	}

	private static int gc(long begin, IntStack toFree, IntStack failed) {
		if(toFree.isEmpty() || isExpired(begin))
			return 0;

		int originalToFree = toFree.size;

		final int chunk = 100;
		int counter = 0;

		outer: while (!toFree.isEmpty()) {
			if(counter++ % chunk == 0) {
				if(isExpired(begin)) {
					break;
				}
			}

			int handle = toFree.pop();

			for(int i = gc_heaps.size() - 1; i >= 0; i--) {
				StructHeap heap = gc_heaps.get(i);
				if(!heap.freeHandle(handle))
					continue;

				if(heap.isEmpty()) {
					if(gc_heaps.remove(i) != heap)
						throw new IllegalStateException();
					empty_heaps.add(heap);
				}
				continue outer;
			}

			failed.push(handle);
		}

		int found = (originalToFree - toFree.size);

		int kick = (int) Math.floor(toFree.size * gc_fail_ratio);
		for(int i = 0; i < kick; i++)
			//	while (!toFree.isEmpty())
			failed.push(toFree.pop());

		return found;
	}

	private static boolean isExpired(long begin) {
		return (System.nanoTime() - begin) / 1_000L > gc_max_micros;
	}

	private static final List<GcInfo> gc_info_callbacks = new ArrayList<>();

	public static void addListener(GcInfo infoCallback) {
		if(infoCallback == null)
			throw new NullPointerException();

		synchronized (gc_info_callbacks) {
			gc_info_callbacks.add(infoCallback);
		}
	}

	public static interface GcInfo {
		public void onGC(int gcHeaps, int idleHeaps, int freedHandles, int[] remainingHandles, long tookNanos);
	}

	public static class IntStack {
		private int[] values = new int[16];
		private int size = 0;

		public void push(int value) {
			if(size == values.length)
				values = Arrays.copyOf(values, values.length * 2);
			values[size++] = value;
		}

		public boolean isEmpty() {
			return size == 0;
		}

		public int pop() {
			return values[--size];
		}

		public void clear() {
			size = 0;
		}
	}

	public static class IntList {
		private int[] values = new int[16];
		private int size = 0;

		public void add(int value) {
			if(size == values.length)
				values = Arrays.copyOf(values, values.length * 2);
			values[size++] = value;
		}

		public boolean contains(int value) {
			return this.indexOf(value) != -1;
		}

		public int indexOf(int value) {
			for(int i = 0; i < size; i++)
				if(values[i] == value)
					return i;
			return -1;
		}

		public int get(int index) {
			if(index >= size)
				throw new IllegalStateException();
			return values[index];
		}

		public int removeIndex(int index) {
			if(index < 0 || index >= size)
				throw new IllegalStateException();
			System.arraycopy(values, index + 1, values, index, size - index - 1);
			int got = values[index];
			size--;
			return got;
		}

		public boolean removeValue(int value) {
			int io = this.indexOf(value);
			if(io == -1)
				return false;
			this.removeIndex(io);
			return true;
		}

		public boolean isEmpty() {
			return size == 0;
		}

		public int size() {
			return size;
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			sb.append(StructGC.class.getSimpleName()).append("[");
			for(int i = 0; i < size; i++)
				sb.append(values[i]).append(',');
			sb.append("]");
			return sb.toString();
		}
	}
}
