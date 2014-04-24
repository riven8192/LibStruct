package net.indiespot.struct.runtime;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class StructMemory {
	private static final boolean manually_fill_and_copy = true;
	private static final boolean check_read_write = false;
	private static final List<ByteBuffer> immortable_buffers = new ArrayList<>();
	private static final StructAllocationStack threadLocalStack;
	static {
		ByteBuffer bb = ByteBuffer.allocateDirect(1024 * 1024);
		immortable_buffers.add(bb);

		long base = StructUnsafe.getBufferBaseAddress(bb);
		int handleOffset = pointer2handle(base);

		threadLocalStack = new StructAllocationStack(handleOffset, (4 * 3) * (int) (1024 * 2.5));
	}

	public static int allocate(int sizeof) {
		int handle = threadLocalStack.allocate(sizeof);

		if(manually_fill_and_copy) {
			fillMemoryByWord(handle, sizeof2words(sizeof), 0x00000000);
		}
		else {
			StructUnsafe.UNSAFE.setMemory(//
					handle2pointer(handle),//
					sizeof,//
					(byte) 0x00);
		}

		return handle;
	}

	public static int allocateCopy(int srcHandle, int sizeof) {
		int dstHandle = threadLocalStack.allocate(sizeof);

		if(manually_fill_and_copy) {
			copyMemoryByWord(srcHandle, dstHandle, sizeof2words(sizeof));
		}
		else {
			StructUnsafe.UNSAFE.copyMemory(//
					handle2pointer(srcHandle),//
					handle2pointer(dstHandle),//
					sizeof);
		}

		return dstHandle;
	}

	public static int[] allocateArray(int length, int sizeof) {
		int handle = threadLocalStack.allocate(sizeof * length);
		fillMemoryByWord(handle, sizeof * length, 0x00000000);
		int[] arr = new int[length];
		for(int i = 0; i < arr.length; i++)
			arr[i] = handle + i;
		return arr;
	}

	private static final void fillMemoryByWord(int handle, int count, int value) {
		long p = handle2pointer(handle);
		for(int i = 0; i < count; i++) {
			int off = (i << 2);
			StructUnsafe.UNSAFE.putInt(p + off, value);
		}
	}

	private static final void copyMemoryByWord(int src, int dst, int count) {
		long pSrc = handle2pointer(src);
		long pDst = handle2pointer(dst);
		for(int i = 0; i < count; i++) {
			int off = (i << 2);
			StructUnsafe.UNSAFE.putInt(pSrc + off, StructUnsafe.UNSAFE.getInt(pDst + off));
		}
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

	public static int sizeof2words(int sizeof) {
		return sizeof >> 2;
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
