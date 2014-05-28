package net.indiespot.struct.runtime;

import java.nio.ByteBuffer;

import net.indiespot.struct.runtime.StructGC.IntList;
import net.indiespot.struct.transform.StructEnv;

public class StructHeap {
	@SuppressWarnings("unused")
	private static final boolean TRACK_EVERY_HANDLE = StructEnv.SAFETY_FIRST || false;

	final ByteBuffer buffer;

	private final StructAllocationBlock block;
	private int allocCount, freeCount;
	private IntList activeHandles;

	public StructHeap(ByteBuffer buffer) {
		long addr = StructMemory.alignBufferToWord(buffer);
		int handleOffset = StructMemory.pointer2handle(addr);

		this.buffer = buffer;
		this.block = new StructAllocationBlock(handleOffset, buffer.remaining());

		if(TRACK_EVERY_HANDLE) {
			this.activeHandles = new IntList();
		}
	}

	public int malloc(int sizeof) {
		if(block.canAllocate(sizeof)) {
			int handle = block.allocate(sizeof);
			if(TRACK_EVERY_HANDLE) {
				if(activeHandles.contains(handle))
					throw new IllegalStateException();
				activeHandles.add(handle);
			}
			allocCount++;
			return handle;
		}
		return 0;
	}

	public int malloc(int sizeof, int length) {
		if(block.canAllocate(sizeof * length)) {
			int offset = block.allocate(sizeof * length);
			if(TRACK_EVERY_HANDLE) {
				for(int i = 0; i < length; i++) {
					int handle = offset + i * StructMemory.bytes2words(sizeof);
					if(activeHandles.contains(handle))
						throw new IllegalStateException();
					activeHandles.add(handle);
				}
			}
			allocCount += length;
			return offset;
		}
		return 0;
	}

	public boolean freeHandle(int handle) {
		if(this.isOnHeap(handle)) {
			if(TRACK_EVERY_HANDLE) {
				if(!activeHandles.removeValue(handle))
					throw new IllegalStateException();
			}
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
		boolean isEmpty = (allocCount == freeCount);
		if(TRACK_EVERY_HANDLE)
			if(isEmpty != activeHandles.isEmpty())
				throw new IllegalStateException();
		return isEmpty;
	}

	public int getHandleCount() {
		int count = (allocCount - freeCount);
		if(TRACK_EVERY_HANDLE)
			if(count != activeHandles.size())
				throw new IllegalStateException();
		return count;
	}

	@Override
	public String toString() {
		return StructHeap.class.getSimpleName() + "[alloc=" + allocCount + ", free=" + freeCount + ", buffer=" + buffer + "]";
	}
}
