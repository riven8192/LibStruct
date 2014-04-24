package net.indiespot.struct.runtime;

import java.nio.ByteBuffer;

public class StructThreadLocalStack {

	public static StructAllocationStack saveStack() {
		StructAllocationStack sas = get();
		sas.save();
		return sas;
	}

	private static final StructAllocationStack[] threadid2stack = new StructAllocationStack[1_000_000];

	public static final StructAllocationStack get() {
		//return tlab.get();
		int id = (int) Thread.currentThread().getId();
		StructAllocationStack stack = threadid2stack[id];
		if(stack == null) {
			threadid2stack[id] = stack = tlab.get();
		}
		return stack;
	}

	private static final ThreadLocal<StructAllocationStack> tlab = new ThreadLocal<StructAllocationStack>() {
		protected StructAllocationStack initialValue() {
			ByteBuffer bb = ByteBuffer.allocateDirect(256 * 1024);

			synchronized (StructMemory.immortable_buffers) {
				StructMemory.immortable_buffers.add(bb);
			}

			long addr = StructMemory.alignBufferToWord(bb);
			int handleOffset = StructMemory.pointer2handle(addr);

			return new StructAllocationStack(handleOffset, bb.remaining());
		};
	};
}
