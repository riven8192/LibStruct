package net.indiespot.struct.runtime;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class StructGC {
	public static StructHeap createThreadLocalHeap() {
		return null;
	}

	private static final int heap_size = 16 * (4 * 1024);
	private static List<StructHeap> local_heaps = new ArrayList<>();
	private static List<StructHeap> gc_heaps = new ArrayList<>();
	private static List<StructHeap> empty_heaps = new ArrayList<>();
	private static IntStack freed_handles = new IntStack();

	public static StructHeap newHeap() {
		synchronized (StructGC.class) {
			if(!empty_heaps.isEmpty()) {
				return empty_heaps.remove(empty_heaps.size() - 1);
			}
		}

		ByteBuffer bb = ByteBuffer.allocateDirect(heap_size);
		bb.order(ByteOrder.nativeOrder());
		StructHeap heap = new StructHeap(bb);
		synchronized (StructGC.class) {
			local_heaps.add(heap);
		}
		return heap;
	}

	public static void gcHeap(StructHeap heap) {
		synchronized (StructGC.class) {
			gc_heaps.add(heap);
		}
	}

	public static void free(int handle) {
		synchronized (StructGC.class) {
			freed_handles.push(handle);
		}
	}

	private static void gc() {
		if(freed_handles.isEmpty())
			return;

		IntStack remainingFreedHandles = new IntStack();

		while (!freed_handles.isEmpty()) {
			int handle = freed_handles.pop();

			for(int i = gc_heaps.size() - 1; i >= 0; i--) {
				StructHeap heap = gc_heaps.get(i);
				if(!heap.free(handle))
					continue;

				if(heap.isEmpty()) {
					gc_heaps.remove(i);
					empty_heaps.add(heap);
				}
				break;
			}

			remainingFreedHandles.push(handle);
		}

		freed_handles = remainingFreedHandles;
	}

	static class IntStack {
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
	}
}
