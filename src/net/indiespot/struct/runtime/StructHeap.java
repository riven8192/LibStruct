package net.indiespot.struct.runtime;

import java.nio.ByteBuffer;

import net.indiespot.struct.runtime.StructGC.LongList;
import net.indiespot.struct.transform.StructEnv;

public class StructHeap {

	final ByteBuffer buffer;

	private final StructAllocationBlock block;
	private int allocCount, freeCount;
	private LongList activeHandles;

	public StructHeap(ByteBuffer buffer) {
		long addr = StructMemory.alignBuffer(buffer, StructMemory.JVMWORD_ALIGNMENT);

		this.buffer = buffer;
		this.block = new StructAllocationBlock(addr, buffer.remaining());

		if (StructEnv.SAFETY_FIRST) {
			this.activeHandles = new LongList();
		}
	}

	public long malloc(int sizeof, boolean dummy, int alignment) {
		if (!block.canAllocate(sizeof + (alignment - 1)))
			return 0;
		long address = block.allocate(sizeof, alignment);
		
		if (StructEnv.SAFETY_FIRST) {
			if (activeHandles.contains(address))
				throw new IllegalStateException();
			activeHandles.add(address);
		}
		allocCount++;
		return address;
	}

	public long malloc(int sizeof, int length, int alignment) {
		if (!block.canAllocate(sizeof * length + (alignment - 1)))
			return 0;
		long address = block.allocate(sizeof * length, alignment);

		if (StructEnv.SAFETY_FIRST) {
			for (long handle : StructMemory.createPointerArray(address, sizeof, length)) {
				if (activeHandles.contains(handle))
					throw new IllegalStateException();
				activeHandles.add(handle);
			}
		}
		allocCount += length;
		return address;
	}

	public boolean freeHandle(long handle) {
		if (!this.isOnHeap(handle))
			return false;
		if (StructEnv.SAFETY_FIRST)
			if (!activeHandles.removeValue(handle))
				throw new IllegalStateException();

		if (++freeCount == allocCount) {
			allocCount = 0;
			freeCount = 0;
			block.reset();
		}
		return true;
	}

	public boolean isOnHeap(long handle) {
		return block.isOnBlock(handle);
	}

	public boolean isEmpty() {
		boolean isEmpty = (allocCount == freeCount);
		if (StructEnv.SAFETY_FIRST)
			if (isEmpty != activeHandles.isEmpty())
				throw new IllegalStateException();
		return isEmpty;
	}

	public int getHandleCount() {
		int count = (allocCount - freeCount);
		if (StructEnv.SAFETY_FIRST)
			if (count != activeHandles.size())
				throw new IllegalStateException();
		return count;
	}

	public int getHandleCountEstimate() {
		return (allocCount - freeCount);
	}

	@Override
	public String toString() {
		return StructHeap.class.getSimpleName() + "[alloc=" + allocCount + ", free=" + freeCount + ", buffer=" + buffer + "]";
	}
}
