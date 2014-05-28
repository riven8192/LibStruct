package net.indiespot.struct.runtime;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class StructGC {
	private static volatile long gc_min_interval = 10;
	private static volatile long gc_max_interval = 2500;
	private static volatile float gc_inc_interval = 1.2f;
	private static volatile float gc_dec_interval = 0.5f;
	private static volatile long gc_max_micros = 1000;
	private static final int heap_size = 16 * (4 * 1024); // 64K
	private static final boolean gc_verbose = true;
	private static final int gc_min_region_collect_count = 2;

	public static int malloc(int sizeof) {
		if(sizeof > heap_size)
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

		if(sizeof * length > heap_size) {
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

	private static final Object sync = new Object();

	private static class RegionTree {
		private static boolean triple_check = false;
		private final List<MemoryRegion> regions;

		public RegionTree() {
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
		private static final IntStack sync_frees = new IntStack();
		private static final List<StructHeap> sync_heaps = new ArrayList<>();
		private static final RegionTree sync_regions = new RegionTree();
		private static final int region_size = heap_size * 8;
		private static final RegionTree gc_regions = new RegionTree();

		public static StructHeap newHeap() {
			synchronized (sync) {
				ByteBuffer bb;

				long addr1, addr2;
				do {
					bb = ByteBuffer.allocateDirect(heap_size);
					bb.order(ByteOrder.nativeOrder());
					addr1 = StructUnsafe.getBufferBaseAddress(bb);
					addr2 = addr1 + heap_size - 1;
				}
				while (addr1 / region_size != addr2 / region_size);

				StructHeap heap = new StructHeap(bb);
				MemoryRegion region = getRegionFor(heap);
				if(region == null) {
					int minWord = calcRegionMinWordForHeap(heap);
					int wordCount = StructMemory.bytes2words(region_size);
					sync_regions.add(new MemoryRegion(minWord, wordCount));
				}
				return heap;
			}
		}

		public static MemoryRegion getRegionFor(int handle) {
			MemoryRegion region;

			region = gc_regions.search(handle);
			if(region != null)
				return region;

			synchronized (sync) {
				region = sync_regions.search(handle);
				if(region != null)
					return region;
			}
			return null;
		}

		public static MemoryRegion getRegionFor(StructHeap heap) {
			// is still brute-force
			for(int i = 0, size = gc_regions.regions.size(); i < size; i++)
				if(gc_regions.regions.get(i).isInRegion(heap))
					return gc_regions.regions.get(i);
			synchronized (sync) {
				for(int i = 0, size = sync_regions.regions.size(); i < size; i++)
					if(sync_regions.regions.get(i).isInRegion(heap))
						return sync_regions.regions.get(i);
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

				if(!sync_regions.regions.isEmpty()) {
					System.out.println("gc_regions: " + gc_regions.regions.size() + " <-- sync_regions: " + sync_regions.regions.size());
				}

				for(MemoryRegion region : sync_regions.regions)
					gc_regions.add(region);
				sync_regions.clear();
			}

			for(int i = 0, size = gc_regions.regions.size(); i < size; i++) {
				int idx = (i + gc_run_id) % size;
				freed += gc_regions.regions.get(idx).gc(begin);

				if(i > gc_min_region_collect_count && isExpired(begin)) {
					break;
				}
			}

			synchronized (sync) {
				while (!Memory.sync_frees.isEmpty()) {
					int handle = Memory.sync_frees.pop();
					getRegionFor(handle).toFree.push(handle);
				}
			}

			int handleCount = 0;
			for(int i = 0, size = gc_regions.regions.size(); i < size; i++)
				handleCount += gc_regions.regions.get(i).getHandleCount();
			Memory.gcRegionsHandleCount = handleCount;

			return freed;
		}

		private static volatile int gcRegionsHandleCount;

		public static int getHandleCount() {
			int handleCount = gcRegionsHandleCount;
			synchronized (sync) {
				for(int i = 0, size = sync_regions.regions.size(); i < size; i++)
					handleCount += sync_regions.regions.get(i).getHandleCount();
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
		return StructMemory.bytes2words(addr / Memory.region_size * Memory.region_size);
	}

	private static class MemoryRegion {
		private final int minWord, wordCount;

		public MemoryRegion(int minWord, int wordCount) {
			if(minWord % (StructMemory.bytes2words(Memory.region_size)) != 0)
				throw new IllegalStateException();
			if(wordCount != StructMemory.bytes2words(Memory.region_size))
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

		public void retryFailedHandles() {
			while (!failed.isEmpty())
				toFree.push(failed.pop());
		}

		public int gc(long begin) {
			this.retryFailedHandles(); // FIXME
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

						synchronized (sync) {
							sync_empty_heaps.add(heap);
						}
					}
					continue outer;
				}

				failed.push(handle);
			}

			return (originalToFree - toFree.size);
		}

		public int getHandleCount() {
			int count = 0;
			for(int i = gcHeaps.size() - 1; i >= 0; i--)
				count += gcHeaps.get(i).getHandleCount();
			return count;
		}
	}

	private static final List<StructHeap> sync_empty_heaps = new ArrayList<>();
	public static FastThreadLocal<StructHeap> local_heaps = new FastThreadLocal<StructHeap>() {
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
					sync_empty_heaps.add(heap);
				else
					Memory.sync_heaps.add(heap);
			}
		}
	};

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

	static {
		Thread thread = new Thread(new Runnable() {
			@Override
			@SuppressWarnings("unused")
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
					if(freed > 0 && gc_verbose)
						System.out.println("LibStructGC freed " + freed + " handles in " + (took / 1000) + "us");

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
		thread.setPriority(Thread.NORM_PRIORITY);
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
