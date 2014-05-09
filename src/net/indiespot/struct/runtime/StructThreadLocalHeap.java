package net.indiespot.struct.runtime;

public class StructThreadLocalHeap {
	public static int malloc(int sizeof) {
		StructHeap heap = StructThreadLocalHeap.getHeap();

		int handle = heap.malloc(sizeof);
		if(handle == 0) {
			StructGC.gcHeap(heap);

			StructThreadLocalHeap.setHeap(heap = StructGC.newHeap());
			handle = heap.malloc(sizeof);
			if(handle == 0)
				throw new IllegalStateException();
		}

		return handle;
	}

	public static void free(int handle) {
		if(!StructThreadLocalHeap.getHeap().free(handle)) {
			StructGC.free(handle);
		}
	}

	private static final StructHeap[] threadid2heap = new StructHeap[100_000];

	public static final StructHeap getHeap() {
		int id = (int) Thread.currentThread().getId();
		StructHeap heap = threadid2heap[id];
		if(heap == null)
			threadid2heap[id] = heap = StructGC.newHeap();
		return heap;
	}

	public static final void setHeap(StructHeap heap) {
		int id = (int) Thread.currentThread().getId();
		threadid2heap[id] = heap;
	}
}
