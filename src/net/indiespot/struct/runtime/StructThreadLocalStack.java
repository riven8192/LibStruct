package net.indiespot.struct.runtime;

import java.nio.ByteBuffer;

public class StructThreadLocalStack {
	public static StructAllocationStack saveStack() {
		StructAllocationStack sas = getStack();
		sas.save();
		return sas;
	}

	private static final StructAllocationStack[] threadid2stack = new StructAllocationStack[100_000];

	public static final StructAllocationStack getStack() {
		int id = (int) Thread.currentThread().getId();
		StructAllocationStack stack = threadid2stack[id];
		if(stack == null)
			threadid2stack[id] = stack = make();
		return stack;
	}

	private static final StructAllocationStack make() {
		ByteBuffer bb = ByteBuffer.allocateDirect(256 * 1024);

		synchronized (StructMemory.immortable_buffers) {
			StructMemory.immortable_buffers.add(bb);
		}

		long addr = StructMemory.alignBufferToWord(bb);
		int handleOffset = StructMemory.pointer2handle(addr);

		return new StructAllocationStack(handleOffset, bb.remaining());
	}
}
