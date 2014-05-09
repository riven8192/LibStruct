package net.indiespot.struct.runtime;

import java.nio.ByteBuffer;

public class StructHeap {
	private final ByteBuffer buffer;

	private final StructAllocationBlock block;
	private int allocCount, freeCount;

	public StructHeap(ByteBuffer buffer) {
		long addr = StructMemory.alignBufferToWord(buffer);
		int handleOffset = StructMemory.pointer2handle(addr);

		this.buffer = buffer;
		this.block = new StructAllocationBlock(handleOffset, buffer.remaining());
	}

	public int malloc(int sizeof) {
		if(block.canAllocate(sizeof)) {
			return block.allocate(sizeof);
		}
		return 0;
	}

	public boolean free(int handle) {
		if(this.isOnHeap(handle)) {
			if(++freeCount == allocCount) {
				allocCount = 0;
				freeCount = 0;
				block.wordsAllocated = 0;
			}
			return true;
		}
		return false;
	}

	public boolean isOnHeap(int handle) {
		return block.isOnBlock(handle);
	}

	public boolean isEmpty() {
		return allocCount == freeCount;
	}
}
