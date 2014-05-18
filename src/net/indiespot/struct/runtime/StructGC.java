package net.indiespot.struct.runtime;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class StructGC {
	public static int malloc(int sizeof) {
		if (sizeof > heap_size)
			throw new IllegalArgumentException();

		StructHeap localHeap = local_heaps.get();
		int handle = localHeap.malloc(sizeof);
		if (handle == 0) {
			// try again with a new heap
			local_heaps.set(newHeap());
			localHeap = local_heaps.get();
			handle = localHeap.malloc(sizeof);
			if (handle == 0)
				throw new IllegalStateException();
		}
		return handle;
	}

	public static int[] malloc(int sizeof, int length) {
		if (length <= 0)
			throw new IllegalArgumentException();

		if (sizeof * length > heap_size) {
			int[] handles = new int[length];
			for (int i = 0; i < length; i++)
				handles[i] = malloc(sizeof);
			return handles;
		}

		StructHeap localHeap = local_heaps.get();
		int handle = localHeap.malloc(sizeof, length);
		if (handle == 0) {
			// try again with a new heap
			local_heaps.set(newHeap());
			localHeap = local_heaps.get();
			handle = localHeap.malloc(sizeof, length);
			if (handle == 0)
				throw new IllegalStateException();
		}

		int[] handles = new int[length];
		for (int i = 0; i < length; i++)
			handles[i] = handle + i * StructMemory.bytes2words(sizeof);
		return handles;
	}

	public static void freeHandle(int handle) {
		StructHeap localHeap = local_heaps.get();
		boolean freedFromLocalHeap = localHeap.freeHandle(handle);

		if (!freedFromLocalHeap) {
			synchronized (sync) {
				Memory.sync_frees.push(handle);
			}
		}
	}

	public static int getHandleCount() {
		return Memory.getHandleCount();
	}

	public static void discardThreadLocal() {
		local_heaps.set(null);
	}

	private static StructHeap newHeap() {
		synchronized (sync) {
			if (!empty_heaps.isEmpty()) {
				StructHeap heap = empty_heaps.remove(empty_heaps.size() - 1);
				if (heap == null || !heap.isEmpty())
					throw new IllegalStateException();
				return heap;
			}
		}

		return Memory.newHeap();
	}

	private static final Object sync = new Object();

	private static class Memory {
		private static final IntStack sync_frees = new IntStack();
		private static final List<MemoryRegion> sync_regions = new ArrayList<>();
		private static final int region_size = heap_size * 8;
		private static final List<MemoryRegion> gc_regions = new ArrayList<>();

		public static StructHeap newHeap() {
			ByteBuffer bb;

			long addr1, addr2;
			do {
				bb = ByteBuffer.allocateDirect(heap_size);
				bb.order(ByteOrder.nativeOrder());
				addr1 = StructUnsafe.getBufferBaseAddress(bb);
				addr2 = addr1 + heap_size - 1;
			} while (addr1 / region_size != addr2 / region_size);

			StructHeap heap = new StructHeap(bb);
			MemoryRegion region = getRegionFor(heap);
			if (region == null) {
				int minWord = StructMemory.bytes2words(addr1 / region_size * region_size);
				int wordCount = StructMemory.bytes2words(region_size);
				synchronized (sync) {
					sync_regions.add(new MemoryRegion(minWord, wordCount));
				}
			}
			return heap;
		}

		public static MemoryRegion getRegionFor(int handle) {
			for (int i = 0, size = gc_regions.size(); i < size; i++)
				if (gc_regions.get(i).isInRegion(handle))
					return gc_regions.get(i);
			synchronized (sync) {
				for (int i = 0, size = sync_regions.size(); i < size; i++)
					if (sync_regions.get(i).isInRegion(handle))
						return sync_regions.get(i);
			}
			return null;
		}

		public static MemoryRegion getRegionFor(StructHeap heap) {
			for (int i = 0, size = gc_regions.size(); i < size; i++)
				if (gc_regions.get(i).isInRegion(heap))
					return gc_regions.get(i);
			synchronized (sync) {
				for (int i = 0, size = sync_regions.size(); i < size; i++)
					if (sync_regions.get(i).isInRegion(heap))
						return sync_regions.get(i);
			}
			return null;
		}

		public static int gc(long begin) {
			int freed = 0;

			synchronized (sync) {
				gc_regions.addAll(sync_regions);
				sync_regions.clear();
			}

			for (int i = 0, size = gc_regions.size(); i < size; i++)
				freed += gc_regions.get(i).gc(begin);

			synchronized (sync) {
				while (!Memory.sync_frees.isEmpty()) {
					int handle = Memory.sync_frees.pop();
					getRegionFor(handle).toFree.push(handle);
				}
			}

			int handleCount = 0;
			for (int i = 0, size = gc_regions.size(); i < size; i++)
				handleCount += gc_regions.get(i).getHandleCount();
			Memory.gcRegionsHandleCount = handleCount;

			return freed;
		}

		private static volatile int gcRegionsHandleCount;

		public static int getHandleCount() {
			int handleCount = gcRegionsHandleCount;
			synchronized (sync) {
				for (int i = 0, size = sync_regions.size(); i < size; i++)
					handleCount += sync_regions.get(i).getHandleCount();
			}
			final int[] holder = new int[1];
			local_heaps.visit(new FastThreadLocal.Visitor<StructHeap>() {
				@Override
				public void visit(int threadId, StructHeap heap) {
					holder[0] += heap.getHandleCount();
				}
			});
			return handleCount + holder[0];
		}
	}

	private static class MemoryRegion {
		private final int minWord, wordCount;

		public MemoryRegion(int minWord, int wordCount) {
			if (minWord % (StructMemory.bytes2words(Memory.region_size)) != 0)
				throw new IllegalStateException();
			if (wordCount != StructMemory.bytes2words(Memory.region_size))
				throw new IllegalStateException();
			this.minWord = minWord;
			this.wordCount = wordCount;
		}

		final List<StructHeap> gcHeaps = new ArrayList<>();
		final IntStack toFree = new IntStack();
		final IntStack failed = new IntStack();

		public boolean isInRegion(int handle) {
			return (handle >= minWord) && (handle < (minWord + wordCount));
		}

		public boolean isInRegion(StructHeap heap) {
			long addr = StructUnsafe.getBufferBaseAddress(heap.buffer);
			int minWord = StructMemory.bytes2words(addr / Memory.region_size * Memory.region_size);
			return this.minWord == minWord;
		}

		public void retryFailedHandles() {
			while (!failed.isEmpty())
				toFree.push(failed.pop());
		}

		public int gc(long begin) {
			this.retryFailedHandles(); // FIXME

			if (toFree.isEmpty() || isExpired(begin))
				return 0;

			int originalToFree = toFree.size;

			final int chunk = 100;
			int counter = 0;

			outer: while (!toFree.isEmpty()) {
				if (counter++ % chunk == 0) {
					if (isExpired(begin)) {
						break;
					}
				}

				int handle = toFree.pop();

				for (int i = gcHeaps.size() - 1; i >= 0; i--) {
					StructHeap heap = gcHeaps.get(i);
					if (!heap.freeHandle(handle))
						continue;

					// System.out.println("freed handle: " + handle);
					if (heap.isEmpty()) {
						if (gcHeaps.remove(i) != heap)
							throw new IllegalStateException();
						empty_heaps.add(heap);
					}
					continue outer;
				}

				failed.push(handle);
			}

			return (originalToFree - toFree.size);
		}

		public int getHandleCount() {
			int count = 0;
			for (int i = gcHeaps.size() - 1; i >= 0; i--)
				count += gcHeaps.get(i).getHandleCount();
			return count;
		}
	}

	private static final List<StructHeap> empty_heaps = new ArrayList<>();
	public static FastThreadLocal<StructHeap> local_heaps = new FastThreadLocal<StructHeap>() {
		@Override
		public StructHeap initialValue() {
			return newHeap();
		}

		@Override
		public void onRelease(StructHeap heap) {
			synchronized (sync) {
				if (heap.isEmpty())
					empty_heaps.add(heap);
				else
					Memory.getRegionFor(heap).gcHeaps.add(heap);
			}
		}
	};

	private static volatile long gc_min_interval = 10;
	private static volatile long gc_max_interval = 2500;
	private static volatile float gc_inc_interval = 1.2f;
	private static volatile float gc_dec_interval = 0.5f;
	private static volatile long gc_max_micros = 1000;
	private static final int heap_size = 16 * (4 * 1024); // 64K

	public static void configureGarbageCollector(//
	   long minIntervalMillis, //
	   long maxIntervalMillis, //
	   float incIntervalFactor, //
	   long maxDurationMicros,//
	   int heapSize,//
	   int maxEmptyHeaps//
	) {
		if (minIntervalMillis < 0)
			throw new IllegalArgumentException();
		if (maxIntervalMillis < minIntervalMillis)
			throw new IllegalArgumentException();
		if (incIntervalFactor < 1.0f)
			throw new IllegalArgumentException();
		if (maxDurationMicros < 0)
			throw new IllegalArgumentException();
		if (maxEmptyHeaps < 0)
			throw new IllegalArgumentException();

		gc_min_interval = minIntervalMillis;
		gc_max_interval = maxIntervalMillis;
		gc_inc_interval = incIntervalFactor;
		gc_max_micros = maxDurationMicros;
	}

	static {
		Thread thread = new Thread(new Runnable() {
			@Override
			public void run() {
				long sleep = gc_min_interval;

				while (true) {
					try {
						Thread.sleep(sleep);
					} catch (InterruptedException e) {
						// ignore
					}

					long tBegin = System.nanoTime();
					int freed = Memory.gc(tBegin);
					long took = System.nanoTime() - tBegin;
					if (freed > 0)
						System.out.println("LibStructGC freed " + freed + " handles in " + (took / 1000) + "us");

					if (freed == 0)
						sleep = Math.round(sleep * gc_inc_interval);
					else
						sleep = Math.round(sleep * gc_dec_interval);
					// clamp
					sleep = Math.max(sleep, gc_min_interval);
					sleep = Math.min(sleep, gc_max_interval);
				}
			}
		});

		thread.setName("LibStruct-Garbage-Collector");
		thread.setDaemon(true);
		thread.setPriority(Thread.MIN_PRIORITY);
		thread.start();
	}

	private static boolean isExpired(long begin) {
		return (System.nanoTime() - begin) / 1_000L > gc_max_micros;
	}

	private static final List<GcInfo> gc_info_callbacks = new ArrayList<>();

	public static void addListener(GcInfo infoCallback) {
		if (infoCallback == null)
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
			if (size == values.length)
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
			if (size == values.length)
				values = Arrays.copyOf(values, values.length * 2);
			values[size++] = value;
		}

		public boolean contains(int value) {
			return this.indexOf(value) != -1;
		}

		public int indexOf(int value) {
			for (int i = 0; i < size; i++)
				if (values[i] == value)
					return i;
			return -1;
		}

		public int get(int index) {
			if (index >= size)
				throw new IllegalStateException();
			return values[index];
		}

		public int removeIndex(int index) {
			if (index < 0 || index >= size)
				throw new IllegalStateException();
			System.arraycopy(values, index + 1, values, index, size - index - 1);
			int got = values[index];
			size--;
			return got;
		}

		public boolean removeValue(int value) {
			int io = this.indexOf(value);
			if (io == -1)
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
			for (int i = 0; i < size; i++)
				sb.append(values[i]).append(',');
			sb.append("]");
			return sb.toString();
		}
	}
}
