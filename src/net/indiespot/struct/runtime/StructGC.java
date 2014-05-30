package net.indiespot.struct.runtime;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class StructGC {

	private static volatile long gc_min_interval = 10;
	private static volatile long gc_max_interval = 2500;
	private static volatile float gc_inc_interval = 1.2f;
	private static volatile float gc_dec_interval = 0.5f;
	private static volatile long gc_max_micros = 1000;
	private static volatile long gc_stress_timeout = 100; // ms
	private static volatile long gc_panic_timeout = 1000; // ms
	private static final int gc_heap_size = 16 * (4 * 1024); // 64K
	private static final int gc_region_size = gc_heap_size * 16; // 64K*16 = 1M
	private static final int gc_max_empty_heap_pool = 1024; // 1024*64K = 64M
	private static final int gc_max_heaps_in_use = (int) (((long) 1024 * 1024 * 1024) / gc_heap_size); // 1G, this is a rather hard cap... to protect the innocent
	private static final boolean gc_verbose = false;
	private static final int gc_min_region_collect_count = 2;
	private static final List<StructHeap> sync_empty_heaps = new ArrayList<>();
	public static FastThreadLocal<StructHeap> local_heaps;
	private static final int gc_thread_priority = Thread.NORM_PRIORITY;
	private static final Object sync = new Object();

	static {
		local_heaps = new FastThreadLocal<StructHeap>() {
			@Override
			public StructHeap initialValue() {
				return newHeap();
			}

			@Override
			public void onRelease(StructHeap heap) {
				if(heap == null)
					throw new NullPointerException();

				synchronized (sync) {
					if(heap.isEmpty())
						onEmptyHeap(heap);
					else
						Memory.sync_heaps.add(heap);
				}
			}
		};
	}

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
		if(maxEmptyHeaps < 0)
			throw new IllegalArgumentException();

		gc_min_interval = minIntervalMillis;
		gc_max_interval = maxIntervalMillis;
		gc_inc_interval = incIntervalFactor;
		gc_max_micros = maxDurationMicros;
	}

	private static StructHeap newHeap() {
		synchronized (sync) {
			if(!sync_empty_heaps.isEmpty()) {
				StructHeap heap = sync_empty_heaps.remove(sync_empty_heaps.size() - 1);
				if(heap == null || !heap.isEmpty())
					throw new IllegalStateException();
				return heap;
			}
		}

		return Memory.newHeap();
	}

	private static void onEmptyHeap(StructHeap heap) {
		if(!heap.isEmpty())
			throw new IllegalStateException();

		synchronized (sync) {
			if(sync_empty_heaps.size() < gc_max_empty_heap_pool) {
				sync_empty_heaps.add(heap);
			}
			else {
				Memory.in_use_heap_count.decrementAndGet();
			}
		}
	}

	public static int malloc(int sizeof) {
		if(sizeof > gc_heap_size)
			throw new IllegalArgumentException();

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

	public static int[] malloc(int sizeof, int length) {
		if(length <= 0)
			throw new IllegalArgumentException();

		if(sizeof * length > gc_heap_size) {
			int[] handles = new int[length];
			for(int i = 0; i < length; i++)
				handles[i] = malloc(sizeof);
			return handles;
		}

		StructHeap localHeap = local_heaps.get();
		int handle = localHeap.malloc(sizeof, length);
		if(handle == 0) {
			// try again with a new heap
			local_heaps.set(newHeap());
			localHeap = local_heaps.get();
			handle = localHeap.malloc(sizeof, length);
			if(handle == 0)
				throw new IllegalStateException();
		}

		int[] handles = new int[length];
		for(int i = 0; i < length; i++)
			handles[i] = handle + i * StructMemory.bytes2words(sizeof);
		return handles;
	}

	public static void freeHandle(int handle) {
		StructHeap localHeap = local_heaps.get();
		boolean freedFromLocalHeap = localHeap.freeHandle(handle);

		if(!freedFromLocalHeap) {
			synchronized (sync) {
				Memory.sync_frees.push(handle);
			}
		}
	}

	public static void freeHandles(int[] handles) {
		StructHeap localHeap = local_heaps.get();

		boolean allFreedFromLocalHeap = true;
		for(int i = 0; i < handles.length; i++) {
			if(localHeap.freeHandle(handles[i])) {
				handles[i] = 0x00;
			}
			else {
				allFreedFromLocalHeap = false;
			}
		}

		if(!allFreedFromLocalHeap) {
			synchronized (sync) {
				for(int i = 0; i < handles.length; i++) {
					if(handles[i] != 0x00) {
						Memory.sync_frees.push(handles[i]);
						handles[i] = 0x00;
					}
				}
			}
		}
	}

	public static int getHandleCount() {
		return Memory.getHandleCount();
	}

	public static void discardThreadLocal() {
		local_heaps.set(null);
	}

	private static class MemoryRegionSet {
		private static boolean triple_check = false;
		private final List<MemoryRegion> regions;

		public MemoryRegionSet() {
			regions = new ArrayList<>();
		}

		public void clear() {
			regions.clear();
		}

		public void add(MemoryRegion region) {
			int io = this.binarySearch(region.minWord);
			if(io >= 0)
				throw new IllegalStateException();
			regions.add(-(io + 1), region);

			if(triple_check) {
				for(int i = 1; i < regions.size(); i++)
					if(regions.get(i - 1).minWord >= regions.get(i - 0).minWord)
						throw new IllegalStateException();
			}
		}

		public MemoryRegion search(int handle) {
			MemoryRegion found = null;
			if(triple_check) {
				for(MemoryRegion region : regions)
					if(region.isInRegion(handle))
						found = region;
			}

			int io = this.binarySearch(handle);
			if(io < 0)
				if(triple_check && found != null)
					throw new IllegalStateException();
				else
					return null;

			MemoryRegion region = regions.get(io);
			if(!region.isInRegion(handle))
				throw new IllegalStateException();
			if(triple_check && found != region)
				throw new IllegalStateException();
			return region;
		}

		private int binarySearch(int handle) {
			List<MemoryRegion> list = regions;

			int lo = 0;
			int hi = list.size() - 1;

			while (lo <= hi) {
				int midIdx = (lo + hi) >>> 1;
				MemoryRegion mid = list.get(midIdx);

				if(handle < mid.minWord)
					hi = midIdx - 1;
				else if(handle >= mid.minWord + mid.wordCount)
					lo = midIdx + 1;
				else
					return midIdx;
			}
			return -(lo + 1);
		}
	}

	private static class Memory {
		private static final AtomicInteger in_use_heap_count = new AtomicInteger();
		private static final IntStack sync_frees = new IntStack();
		private static final List<StructHeap> sync_heaps = new ArrayList<>();
		private static final MemoryRegionSet sync_region_set = new MemoryRegionSet();
		private static final MemoryRegionSet gc_region_set = new MemoryRegionSet();
		private static volatile int gc_regions_handle_count;

		public static StructHeap newHeap() {
			for(int i = 0; in_use_heap_count.get() >= gc_max_heaps_in_use; i++) {
				try {
					Thread.sleep(1);
				}
				catch (InterruptedException exc) {
					// ignore
				}

				if(i == gc_stress_timeout) {
					synchronized (gc_info_callbacks) {
						for(GcInfo callback : gc_info_callbacks) {
							callback.onPanic();
						}
					}
				}
				else if(i % gc_panic_timeout == gc_panic_timeout - 1) {
					synchronized (gc_info_callbacks) {
						for(GcInfo callback : gc_info_callbacks) {
							callback.onPanic();
						}
					}
				}
			}

			synchronized (sync) {
				ByteBuffer bb;

				// find a region that is completely within one region
				long addr1, addr2;
				do {
					bb = ByteBuffer.allocateDirect(gc_heap_size);
					bb.order(ByteOrder.nativeOrder());
					addr1 = StructUnsafe.getBufferBaseAddress(bb);
					addr2 = addr1 + gc_heap_size - 1;
				}
				while (addr1 / gc_region_size != addr2 / gc_region_size);

				StructHeap heap = new StructHeap(bb);
				in_use_heap_count.incrementAndGet();
				MemoryRegion region = getRegionFor(heap);
				if(region == null) {
					int minWord = calcRegionMinWordForHeap(heap);
					int wordCount = StructMemory.bytes2words(gc_region_size);
					sync_region_set.add(new MemoryRegion(minWord, wordCount));
				}
				return heap;
			}
		}

		public static MemoryRegion getRegionFor(int handle) {
			MemoryRegion region;

			region = gc_region_set.search(handle);
			if(region != null)
				return region;

			synchronized (sync) {
				region = sync_region_set.search(handle);
				if(region != null)
					return region;
			}
			return null;
		}

		public static MemoryRegion getRegionFor(StructHeap heap) {
			// is still brute-force
			for(int i = 0, size = gc_region_set.regions.size(); i < size; i++)
				if(gc_region_set.regions.get(i).isInRegion(heap))
					return gc_region_set.regions.get(i);
			synchronized (sync) {
				for(int i = 0, size = sync_region_set.regions.size(); i < size; i++)
					if(sync_region_set.regions.get(i).isInRegion(heap))
						return sync_region_set.regions.get(i);
			}
			return null;
		}

		private static int gc_run_id = 0;

		public static int gc(long begin) {
			gc_run_id++;
			int freed = 0;

			synchronized (sync) {
				for(StructHeap heap : sync_heaps)
					getRegionFor(heap).gcHeaps.add(heap);
				sync_heaps.clear();

				if(!sync_region_set.regions.isEmpty()) {
					if(gc_verbose)
						System.out.println("LibStructGC gc_regions: " + gc_region_set.regions.size() + " <-- sync_regions: " + sync_region_set.regions.size());
				}

				for(MemoryRegion region : sync_region_set.regions)
					gc_region_set.add(region);
				sync_region_set.clear();
			}

			for(int i = 0, size = gc_region_set.regions.size(); i < size; i++) {
				int idx = (i + gc_run_id) % size;
				freed += gc_region_set.regions.get(idx).gc(begin);

				if(i > gc_min_region_collect_count && isExpired(begin)) {
					break;
				}
			}

			synchronized (sync) {
				// clean up empty regions
				for(int i = gc_region_set.regions.size() - 1; i >= 0; i--) {
					if(!gc_region_set.regions.get(i).gcHeaps.isEmpty())
						continue;
					if(!gc_region_set.regions.get(i).toFree.isEmpty())
						continue;
					gc_region_set.regions.remove(i);
				}

				// 
				while (!Memory.sync_frees.isEmpty()) {
					int handle = Memory.sync_frees.pop();
					getRegionFor(handle).toFree.push(handle);
				}
			}

			int handleCount = 0;
			for(int i = 0, size = gc_region_set.regions.size(); i < size; i++)
				handleCount += gc_region_set.regions.get(i).getHandleCount();
			gc_regions_handle_count = handleCount;

			return freed;
		}

		public static int getHandleCount() {
			int handleCount = gc_regions_handle_count;
			synchronized (sync) {
				for(int i = 0, size = sync_region_set.regions.size(); i < size; i++)
					handleCount += sync_region_set.regions.get(i).getHandleCount();
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

	public static int calcRegionMinWordForHeap(StructHeap heap) {
		long addr = StructUnsafe.getBufferBaseAddress(heap.buffer);
		return StructMemory.bytes2words(addr / gc_region_size * gc_region_size);
	}

	private static class MemoryRegion {
		private final int minWord, wordCount;

		public MemoryRegion(int minWord, int wordCount) {
			if(minWord % (StructMemory.bytes2words(gc_region_size)) != 0)
				throw new IllegalStateException();
			if(wordCount != StructMemory.bytes2words(gc_region_size))
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
			return this.minWord == calcRegionMinWordForHeap(heap);
		}

		public int gc(long begin) {
			if(toFree.isEmpty())
				return 0;

			int originalToFree = toFree.size;

			outer: while (!toFree.isEmpty()) {

				int handle = toFree.pop();

				for(int i = gcHeaps.size() - 1; i >= 0; i--) {
					StructHeap heap = gcHeaps.get(i);
					if(!heap.freeHandle(handle))
						continue;

					if(heap.isEmpty()) {
						if(gcHeaps.remove(i) != heap)
							throw new IllegalStateException();
						onEmptyHeap(heap);
					}
					continue outer;
				}

				failed.push(handle);
			}

			int freed = (originalToFree - toFree.size);
			while (!failed.isEmpty())
				toFree.push(failed.pop());
			return freed;
		}

		public int getHandleCount() {
			int count = 0;
			for(int i = gcHeaps.size() - 1; i >= 0; i--)
				count += gcHeaps.get(i).getHandleCount();
			return count;
		}
	}

	static {
		Thread thread = new Thread(new Runnable() {
			@Override
			public void run() {
				long sleep = (gc_min_interval + gc_max_interval) / 2;

				while (true) {
					try {
						Thread.sleep(sleep);
					}
					catch (InterruptedException e) {
						// ignore
					}

					long tBegin = System.nanoTime();
					int freed = Memory.gc(tBegin);
					long took = System.nanoTime() - tBegin;
					if(freed > 0) {
						int handleCount = Memory.gc_regions_handle_count;

						int gcHeaps = 0;
						for(MemoryRegion region : Memory.gc_region_set.regions)
							gcHeaps += region.gcHeaps.size();

						int emptyHeaps;
						synchronized (sync) {
							emptyHeaps = sync_empty_heaps.size();
						}

						synchronized (gc_info_callbacks) {
							for(GcInfo callback : gc_info_callbacks) {
								callback.onGC(freed, handleCount, gcHeaps, emptyHeaps, took);
							}
						}
					}
					if(freed == 0)
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
		thread.setPriority(gc_thread_priority);
		thread.start();
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
		public void onGC(int freedHandles, int remainingHandles, int gcHeaps, int emptyHeaps, long tookNanos);

		public void onStress();

		public void onPanic();
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
