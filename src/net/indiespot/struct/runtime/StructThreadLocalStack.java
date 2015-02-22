package net.indiespot.struct.runtime;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class StructThreadLocalStack {
	public static StructAllocationStack saveStack() {
		StructAllocationStack sas = getStack();
		sas.save();
		return sas;
	}

	public static final StructAllocationStack getStack() {
		int id = (int) Thread.currentThread().getId();
		StructAllocationStack stack = threadid2stack[id];
		if (stack == null)
			threadid2stack[id] = stack = createThreadLocalStack();
		return stack;
	}

	private static final StructAllocationStack createThreadLocalStack() {
		ByteBuffer buffer = null;
		synchronized (buffer_mutex) {
			if (!discarded_buffers.isEmpty()) {
				buffer = discarded_buffers.remove(discarded_buffers.size() - 1);
				buffer.clear();
			}
		}
		if (buffer == null) {
			buffer = ByteBuffer.allocateDirect(threadlocal_stacksize).order(ByteOrder.nativeOrder());
		}

		int id = (int) Thread.currentThread().getId();
		synchronized (buffer_mutex) {
			threadid2buffer[id] = buffer;
		}

		long addr = StructMemory.alignBuffer(buffer, StructMemory.JVMWORD_ALIGNMENT);
		return new StructAllocationStack(addr, buffer.remaining());
	}

	private static final int threadlocal_stacksize = 256 * 1024;
	private static final Object buffer_mutex = new Object();
	private static final List<ByteBuffer> discarded_buffers = new ArrayList<>();
	private static final ByteBuffer[] threadid2buffer = new ByteBuffer[FastThreadLocal.MAX_SUPPORTED_THREADS];
	private static final StructAllocationStack[] threadid2stack = new StructAllocationStack[FastThreadLocal.MAX_SUPPORTED_THREADS];

	static {
		ThreadMonitor.addListener(new ThreadMonitor.ThreadListener() {
			@Override
			public void onThreadStart(long threadId) {

			}

			@Override
			public void onThreadDeath(long threadId) {
				ByteBuffer buffer = threadid2buffer[(int) threadId];
				threadid2buffer[(int) threadId] = null;

				if (buffer != null) {
					synchronized (buffer_mutex) {
						discarded_buffers.add(buffer);
					}
				}
			}
		});
	}
}
