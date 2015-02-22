package net.indiespot.struct.runtime;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import net.indiespot.struct.cp.StructConfig;
import net.indiespot.struct.transform.StructEnv;

public class StructGC {

	private static volatile long gc_min_interval = 10;
	private static volatile long gc_max_interval = 2500;
	private static volatile float gc_inc_interval = 1.2f;
	private static volatile float gc_dec_interval = 0.5f;
	private static volatile long gc_max_micros = 1000;
	private static volatile long gc_stress_timeout = 100; // ms
	private static volatile long gc_panic_timeout = 1000; // ms
	private static final int gc_heap_size;
	private static final int gc_region_size;
	private static final int gc_max_empty_heap_pool;
	private static final int gc_max_heaps_in_use;
	private static final boolean gc_verbose;
	private static final int gc_min_region_collect_count;

	static {
		final int pageSize = 4 * 1024;

		gc_heap_size = (int) StructConfig.parseVmArg(StructGC.class, "HEAP_SIZE", 16 * pageSize, true);
		gc_region_size = (int) StructConfig.parseVmArg(StructGC.class, "REGION_SIZE", gc_heap_size * 16, true);
		gc_max_empty_heap_pool = (int) StructConfig.parseVmArg(StructGC.class, "MAX_EMPTY_HEAP_POOL", 1000, false);

		long maxMemUse = StructConfig.parseVmArg(StructGC.class, "MAX_MEMORY_USE", 1024L * 1024L * 1024L, true);
		gc_max_heaps_in_use = (int) (maxMemUse / gc_heap_size);

		gc_verbose = StructConfig.parseVmArg(StructGC.class, "VERBOSE", 0L, true) != 0L;
		gc_min_region_collect_count = (int) StructConfig.parseVmArg(StructGC.class, "MIN_REGION_COLLECT_COUNT", 2, false);
	}

	private static final List<StructHeap> sync_empty_heaps = new ArrayList<>();
	public static FastThreadLocal<StructHeap> local_heaps;
	private static final int gc_thread_priority = Thread.NORM_PRIORITY;
	private static final boolean gc_scramble_new_heap_memory = true;
	private static final Object sync = new Object();

	static {
		local_heaps = new FastThreadLocal<StructHeap>() {
			@Override
			public StructHeap initialValue() {
				return newHeap();
			}

			@Override
			public void onRelease(StructHeap heap) {
				if (heap == null)
					throw new NullPointerException();

				synchronized (sync) {
					if (heap.isEmpty())
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

	private static StructHeap newHeap() {
		synchronized (sync) {
			if (!sync_empty_heaps.isEmpty()) {
				StructHeap heap = sync_empty_heaps.remove(sync_empty_heaps.size() - 1);
				if (heap == null || !heap.isEmpty())
					throw new IllegalStateException();
				return heap;
			}
		}

		return Memory.newHeap();
	}

	private static void onEmptyHeap(StructHeap heap) {
		if (!heap.isEmpty())
			throw new IllegalStateException();

		synchronized (sync) {
			if (sync_empty_heaps.size() < gc_max_empty_heap_pool) {
				sync_empty_heaps.add(heap);
			} else {
				Memory.in_use_heap_count.decrementAndGet();
			}
		}
	}

	private static long mallocImpl(long stride, int count, int alignment) {
		if (StructEnv.SAFETY_FIRST)
			if (stride <= 0)
				throw new IllegalArgumentException();
		if (StructEnv.SAFETY_FIRST)
			if (count <= 0)
				throw new IllegalArgumentException();

		long handle;

		if (stride * count > gc_heap_size) {
			LargeMalloc largeMalloc = new LargeMalloc(stride, count, alignment);
			synchronized (large_mallocs) {
				large_mallocs.add(largeMalloc);
			}
			handle = largeMalloc.addr;
		} else {
			handle = local_heaps.get().malloc((int) stride, count, alignment);
			if (handle == 0) {
				// try again with a new heap
				local_heaps.set(newHeap());
				handle = local_heaps.get().malloc((int) stride, count, alignment);
				if (handle == 0)
					throw new IllegalStateException();
			}
		}

		return handle;
	}

	public static long malloc(int sizeof, int alignment) {
		return mallocImpl(sizeof, 1, alignment);
	}

	public static long calloc(int sizeof, int alignment) {
		long handle = malloc(sizeof, alignment);
		StructMemory.clearMemory(handle, sizeof);
		return handle;
	}

	public static long[] mallocArray(int sizeof, int length, int alignment) {
		long pointer = mallocImpl(sizeof, length, alignment);
		return StructMemory.createPointerArray(pointer, sizeof, length);
	}

	public static long[] callocArray(int sizeof, int length, int alignment) {
		long[] handles = mallocArray(sizeof, length, alignment);
		StructMemory.clearMemory(handles[0], (long) sizeof * length);
		return handles;
	}

	public static long mallocArrayBase(int sizeof, int length, int alignment) {
		return mallocImpl((long) sizeof * length, 1, alignment);
	}

	public static long callocArrayBase(int sizeof, int length, int alignment) {
		long baseHandle = mallocArrayBase(sizeof, length, alignment);
		StructMemory.clearMemory(baseHandle, (long) sizeof * length);
		return baseHandle;
	}

	public static long[] reallocArray(int sizeof, long[] src, int newLength, int alignment) {
		if (StructEnv.SAFETY_FIRST)
			for (int i = 0; i < src.length; i++)
				if (src[i] == 0x00)
					throw new NullPointerException("index=" + i);

		long[] dst = mallocArray(sizeof, newLength, alignment);
		int min = Math.min(src.length, newLength);
		for (int i = 0; i < min; i++)
			StructMemory.copy(sizeof, src[i], dst[i]);

		freeHandles(src);
		return dst;
	}

	private static class LargeMalloc {
		private final long base;
		public final long addr;
		public final long sizeof;
		public int unfreedHandles;
		private LongList activeHandles;

		public LargeMalloc(long stride, int count, int alignment) {
			if (StructEnv.SAFETY_FIRST) {
				if (stride <= 0L)
					throw new IllegalStateException();
				if (count <= 0L)
					throw new IllegalStateException();
				StructMemory.verifyAlignment(alignment);
			}

			this.sizeof = stride * count;
			try {
				this.base = StructUnsafe.UNSAFE.allocateMemory(sizeof + alignment);
			} catch (OutOfMemoryError err) {
				throw new OutOfMemoryError("failed to allocate " + sizeof + " bytes");
			}
			this.addr = StructMemory.alignAddress(base, alignment);
			this.unfreedHandles = count;

			if (StructEnv.SAFETY_FIRST) {
				activeHandles = new LongList();

				if (count == 1) {
					if (activeHandles.contains(addr))
						throw new IllegalStateException();
					activeHandles.add(addr);
				} else {
					for (long handle : StructMemory.createPointerArray(addr, (int) stride, count)) {
						if (activeHandles.contains(handle))
							throw new IllegalStateException();
						activeHandles.add(handle);
					}
				}
			}
		}

		public boolean freeHandle(long pntr) {
			if (pntr >= addr && pntr < addr + sizeof) {
				if (StructEnv.SAFETY_FIRST && unfreedHandles <= 0)
					throw new IllegalStateException();
				if (StructEnv.SAFETY_FIRST)
					if (!activeHandles.removeValue(pntr))
						throw new IllegalStateException();
				if (--unfreedHandles == 0) {
					StructUnsafe.UNSAFE.freeMemory(base);
				}
				return true;
			}
			return false;
		}
	}

	private static List<LargeMalloc> large_mallocs = new ArrayList<>();

	public static void freeHandle(long handle) {
		StructHeap localHeap = local_heaps.get();
		boolean freedFromLocalHeap = localHeap.freeHandle(handle);

		if (!freedFromLocalHeap) {
			awaitReasonableSyncQueueSize();

			synchronized (sync) {
				Memory.sync_frees.push(handle);
			}
		}
	}

	public static void freeHandles(long[] handles) {

		StructHeap localHeap = local_heaps.get();

		boolean allFreedFromLocalHeap = true;
		for (int i = 0; i < handles.length; i++) {
			if (localHeap.freeHandle(handles[i])) {
				handles[i] = 0x00;
			} else {
				allFreedFromLocalHeap = false;
			}
		}

		if (!allFreedFromLocalHeap) {
			final int batchSize = 1024;
			int off = 0;
			while (off != handles.length) {
				awaitReasonableSyncQueueSize();
				synchronized (sync) {
					int end = Math.min(off + batchSize, handles.length);
					for (int i = off; i < end; i++) {
						if (handles[i] != 0x00) {
							Memory.sync_frees.push(handles[i]);
							handles[i] = 0x00;
						}
					}
					off = end;
				}
			}
		}
	}

	private static void awaitReasonableSyncQueueSize() {
		while (true) {
			synchronized (sync) {
				if (Memory.sync_frees.size < 10_000_000) {
					return;
				}
			}

			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {
				// ignore
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
		private final List<MemoryRegion> regions;

		public MemoryRegionSet() {
			regions = new ArrayList<>();
		}

		public void clear() {
			regions.clear();
		}

		public void add(MemoryRegion region) {
			int io = this.binarySearch(region.base);
			if (io >= 0)
				throw new IllegalStateException();
			regions.add(-(io + 1), region);

			if (StructEnv.SAFETY_FIRST) {
				for (int i = 1; i < regions.size(); i++)
					if (regions.get(i - 1).base >= regions.get(i - 0).base)
						throw new IllegalStateException();
			}
		}

		public MemoryRegion search(long handle) {
			MemoryRegion found = null;
			if (StructEnv.SAFETY_FIRST) {
				for (MemoryRegion region : regions)
					if (region.isInRegion(handle))
						found = region;
			}

			int io = this.binarySearch(handle);
			if (io < 0)
				if (StructEnv.SAFETY_FIRST && found != null)
					throw new IllegalStateException();
				else
					return null;

			MemoryRegion region = regions.get(io);
			if (!region.isInRegion(handle))
				throw new IllegalStateException();
			if (StructEnv.SAFETY_FIRST && found != region)
				throw new IllegalStateException();
			return region;
		}

		private int binarySearch(long handle) {
			List<MemoryRegion> list = regions;

			int lo = 0;
			int hi = list.size() - 1;

			while (lo <= hi) {
				int midIdx = (lo + hi) >>> 1;
				MemoryRegion mid = list.get(midIdx);

				if (handle < mid.base)
					hi = midIdx - 1;
				else if (handle >= mid.base + mid.sizeof)
					lo = midIdx + 1;
				else
					return midIdx;
			}
			return -(lo + 1);
		}
	}

	private static class Memory {
		private static final AtomicInteger in_use_heap_count = new AtomicInteger();
		private static final LongStack sync_frees = new LongStack();
		private static final List<StructHeap> sync_heaps = new ArrayList<>();
		private static final MemoryRegionSet sync_region_set = new MemoryRegionSet();
		private static final MemoryRegionSet gc_region_set = new MemoryRegionSet();
		private static volatile int gc_regions_handle_count;

		public static StructHeap newHeap() {
			for (int i = 0; in_use_heap_count.get() >= gc_max_heaps_in_use; i++) {
				try {
					Thread.sleep(1);
				} catch (InterruptedException exc) {
					// ignore
				}

				if (i == gc_stress_timeout) {
					synchronized (gc_info_callbacks) {
						for (GcInfo callback : gc_info_callbacks) {
							callback.onStress();
						}
					}
				} else if (i % gc_panic_timeout == gc_panic_timeout - 1) {
					synchronized (gc_info_callbacks) {
						for (GcInfo callback : gc_info_callbacks) {
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
				} while (addr1 / gc_region_size != addr2 / gc_region_size);

				StructHeap heap = new StructHeap(bb);
				in_use_heap_count.incrementAndGet();

				if (gc_scramble_new_heap_memory) {
					IntBuffer ib = heap.buffer.asIntBuffer();
					for (int i = ib.position(), len = ib.limit(); i < len; i++) {
						ib.put(i, 0xCAFEBABE);
					}
				}

				MemoryRegion region = getRegionFor(heap);
				if (region == null) {
					long base = calcRegionBaseForHeap(heap);
					sync_region_set.add(new MemoryRegion(base, gc_region_size));
				}
				return heap;
			}
		}

		public static MemoryRegion getRegionFor(long handle) {
			MemoryRegion region;

			region = gc_region_set.search(handle);
			if (region != null)
				return region;

			synchronized (sync) {
				region = sync_region_set.search(handle);
				if (region != null)
					return region;
			}
			return null;
		}

		public static MemoryRegion getRegionFor(StructHeap heap) {
			// is still brute-force
			for (int i = 0, size = gc_region_set.regions.size(); i < size; i++)
				if (gc_region_set.regions.get(i).isInRegion(heap))
					return gc_region_set.regions.get(i);
			synchronized (sync) {
				for (int i = 0, size = sync_region_set.regions.size(); i < size; i++)
					if (sync_region_set.regions.get(i).isInRegion(heap))
						return sync_region_set.regions.get(i);
			}
			return null;
		}

		private static int gc_run_id = 0;

		public static void gc(long begin, GcStats out) {
			out.runId = gc_run_id++;
			out.freed = 0;
			out.collectedRegionCount = 0;
			int freed = 0;

			synchronized (sync) {
				for (StructHeap heap : sync_heaps)
					getRegionFor(heap).gcHeaps.add(heap);
				sync_heaps.clear();

				if (!sync_region_set.regions.isEmpty()) {
					if (gc_verbose)
						System.out.println("LibStructGC gc_regions: " + gc_region_set.regions.size() + " <-- sync_regions: " + sync_region_set.regions.size());
				}

				for (MemoryRegion region : sync_region_set.regions)
					gc_region_set.add(region);
				sync_region_set.clear();
			}

			out.collectedRegionCount = gc_region_set.regions.size();
			for (int i = 0, size = gc_region_set.regions.size(); i < size; i++) {
				int idx = (i + gc_run_id) % size;
				freed += gc_region_set.regions.get(idx).gc(begin);

				if (i > gc_min_region_collect_count && isExpired(begin)) {
					out.collectedRegionCount = i + 1;
					break;
				}
			}

			synchronized (sync) {
				// clean up empty regions
				if (false)
					for (int i = gc_region_set.regions.size() - 1; i >= 0; i--) {
						if (!gc_region_set.regions.get(i).gcHeaps.isEmpty())
							continue;
						if (!gc_region_set.regions.get(i).toFree.isEmpty())
							continue;
						gc_region_set.regions.remove(i);
					}

				//
				outer: while (!Memory.sync_frees.isEmpty()) {
					long handle = Memory.sync_frees.pop();

					synchronized (large_mallocs) { // TODO: optimize
						for (LargeMalloc largeMalloc : large_mallocs) {
							if (largeMalloc.freeHandle(handle)) {
								if (largeMalloc.unfreedHandles == 0)
									large_mallocs.remove(largeMalloc);
								freed++;
								continue outer;
							}
						}
					}

					getRegionFor(handle).toFree.push(handle);
				}
			}

			int handleCount = 0;
			for (int i = 0, size = gc_region_set.regions.size(); i < size; i++)
				handleCount += gc_region_set.regions.get(i).getHandleCount();
			gc_regions_handle_count = handleCount;

			out.freed = freed;
		}

		public static int getHandleCount() {
			int handleCount = gc_regions_handle_count;
			synchronized (sync) {
				for (int i = 0, size = sync_region_set.regions.size(); i < size; i++)
					handleCount += sync_region_set.regions.get(i).getHandleCount();
			}
			synchronized (large_mallocs) {
				for (int i = 0, size = large_mallocs.size(); i < size; i++)
					handleCount += large_mallocs.get(i).unfreedHandles;
			}
			final int[] holder = new int[1];
			local_heaps.visit(new FastThreadLocal.Visitor<StructHeap>() {
				@Override
				public void visit(int threadId, StructHeap heap) {
					holder[0] += heap.getHandleCountEstimate();
				}
			});
			return handleCount + holder[0];
		}
	}

	public static class GcStats implements Cloneable {
		public int runId;
		public int freed;
		public int remaining;
		public int collectedRegionCount;
		public long tookNanos;

		public GcStats copy() {
			GcStats copy = new GcStats();
			copy.runId = this.runId;
			copy.freed = this.freed;
			copy.remaining = this.remaining;
			copy.collectedRegionCount = this.collectedRegionCount;
			copy.tookNanos = this.tookNanos;
			return copy;
		}
	}

	public static long calcRegionBaseForHeap(StructHeap heap) {
		long addr = StructUnsafe.getBufferBaseAddress(heap.buffer);
		return addr / gc_region_size * gc_region_size;
	}

	private static class MemoryRegion {
		private final long base;
		private final int sizeof;

		public MemoryRegion(long base, int sizeof) {
			if (base % gc_region_size != 0)
				throw new IllegalStateException();
			if (sizeof != gc_region_size)
				throw new IllegalStateException();
			this.base = base;
			this.sizeof = sizeof;
		}

		final List<StructHeap> gcHeaps = new ArrayList<>();
		final LongStack toFree = new LongStack();
		final LongStack failed = new LongStack();

		public boolean isInRegion(long handle) {
			return (handle >= base) && (handle < (base + sizeof));
		}

		public boolean isInRegion(StructHeap heap) {
			return this.base == calcRegionBaseForHeap(heap);
		}

		public int gc(long begin) {
			if (toFree.isEmpty())
				return 0;

			int originalToFree = toFree.size;

			outer: while (!toFree.isEmpty()) {

				long handle = toFree.pop();

				for (int i = gcHeaps.size() - 1; i >= 0; i--) {
					StructHeap heap = gcHeaps.get(i);
					if (!heap.freeHandle(handle))
						continue;

					if (heap.isEmpty()) {
						if (gcHeaps.remove(i) != heap)
							throw new IllegalStateException();
						onEmptyHeap(heap);
					}
					continue outer;
				}

				failed.push(handle);
			}

			while (!failed.isEmpty())
				toFree.push(failed.pop());
			return originalToFree - toFree.size;
		}

		public int getHandleCount() {
			int count = 0;
			for (int i = gcHeaps.size() - 1; i >= 0; i--)
				count += gcHeaps.get(i).getHandleCount();
			return count;
		}
	}

	static {
		Thread thread = new Thread(new Runnable() {
			@Override
			public void run() {
				long sleep = (gc_min_interval + gc_max_interval) / 2;
				GcStats stats = new GcStats();

				while (true) {
					try {
						Thread.sleep(sleep);
					} catch (InterruptedException e) {
						// ignore
					}

					long tBegin = System.nanoTime();
					Memory.gc(tBegin, stats);
					stats.tookNanos = System.nanoTime() - tBegin;

					if (stats.freed > 0) {
						stats.remaining = Memory.getHandleCount();

						int gcHeaps = 0;
						for (MemoryRegion region : Memory.gc_region_set.regions)
							gcHeaps += region.gcHeaps.size();

						int emptyHeaps;
						synchronized (sync) {
							emptyHeaps = sync_empty_heaps.size();
						}

						synchronized (gc_info_callbacks) {
							for (GcInfo callback : gc_info_callbacks) {
								callback.onGC(stats.copy());
							}
						}
					}
					if (stats.freed == 0)
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
		if (infoCallback == null)
			throw new NullPointerException();

		synchronized (gc_info_callbacks) {
			gc_info_callbacks.add(infoCallback);
		}
	}

	public static interface GcInfo {
		public void onGC(GcStats stats);

		public void onStress();

		public void onPanic();
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

	public static class LongStack {
		private long[] values = new long[16];
		private int size = 0;

		public void push(long value) {
			if (size == values.length)
				values = Arrays.copyOf(values, values.length * 2);
			values[size++] = value;
		}

		public boolean isEmpty() {
			return size == 0;
		}

		public long pop() {
			return values[--size];
		}

		public void clear() {
			size = 0;
		}
	}

	public static class LongList {
		private long[] values = new long[16];
		private int size = 0;

		public void add(long value) {
			if (size == values.length)
				values = Arrays.copyOf(values, values.length * 2);
			values[size++] = value;
		}

		public boolean contains(long value) {
			return this.indexOf(value) != -1;
		}

		public int indexOf(long value) {
			for (int i = 0; i < size; i++)
				if (values[i] == value)
					return i;
			return -1;
		}

		public long get(int index) {
			if (index >= size)
				throw new IllegalStateException();
			return values[index];
		}

		public long removeIndex(int index) {
			if (index < 0 || index >= size)
				throw new IllegalStateException();
			System.arraycopy(values, index + 1, values, index, size - index - 1);
			long got = values[index];
			size--;
			return got;
		}

		public boolean removeValue(long value) {
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
