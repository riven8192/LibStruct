package net.indiespot.struct.runtime;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class StructMemory {
	private static final boolean check_read_write = true;
	private static final List<ByteBuffer> retain = new ArrayList<>();
	private static final StructAllocationStack threadLocalStack;
	static {
		ByteBuffer bb = ByteBuffer.allocateDirect(1024 * 1024);
		retain.add(bb);

		long base = StructUnsafe.getBufferBaseAddress(bb);
		int handleOffset = pointer2handle(base);

		threadLocalStack = new StructAllocationStack(handleOffset, (4 * 3) * 1024);
	}

	public static int allocate(int sizeof) {
		int handle = threadLocalStack.allocate(sizeof);
		StructUnsafe.UNSAFE.setMemory(//
				handle2pointer(handle),//
				sizeof,//
				(byte) 0x00);
		return handle;
	}

	public static int allocateCopy(int srcHandle, int sizeof) {
		int dstHandle = threadLocalStack.allocate(sizeof);
		StructUnsafe.UNSAFE.copyMemory(//
				handle2pointer(srcHandle),//
				handle2pointer(dstHandle),//
				sizeof);
		return dstHandle;
	}

	public static boolean isValid(int handle) {
		return threadLocalStack.isOnStack(handle);
	}

	public static void saveStack() {
		threadLocalStack.save();
	}

	public static void restoreStack() {
		threadLocalStack.restore();
	}

	public static void execCheckcastInsn() {
		throw new ClassCastException("cannot cast structs to a class");
	}

	public static int[] allocateArray(int length, int sizeof) {
		int[] handles = new int[length];
		for(int i = 0; i < handles.length; i++)
			handles[i] = allocate(sizeof);
		return handles;
	}

	public static long handle2pointer(int handle) {
		return (handle & 0xFFFF_FFFFL) << 2;
	}

	public static int pointer2handle(long pointer) {
		if(pointer < 0L || (pointer & 3) != 0)
			throw new IllegalStateException();
		long handle = pointer >> 2;
		if(handle > 0xFFFF_FFFFL)
			throw new IllegalStateException();
		return (int) handle;
	}

	public static final void write(int handle, float value, int fieldOffset) {
		if(check_read_write) {
			if(threadLocalStack.isOnBlock(handle) && !threadLocalStack.isOnStack(handle)) {
				throw new IllegalStackAccessError();
			}
		}
		StructUnsafe.UNSAFE.putFloat(handle2pointer(handle) + fieldOffset, value);
	}

	public static final float read(int handle, int fieldOffset) {
		if(check_read_write) {
			if(threadLocalStack.isOnBlock(handle) && !threadLocalStack.isOnStack(handle)) {
				throw new IllegalStackAccessError();
			}
		}
		return StructUnsafe.UNSAFE.getFloat(handle2pointer(handle) + fieldOffset);
	}
}
