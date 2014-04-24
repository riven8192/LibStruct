package net.indiespot.struct.runtime;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class StructMemory {
	public static final boolean CHECK_ALLOC_OVERFLOW = !true;
	public static final boolean CHECK_MEMORY_ACCESS_REGION = !true;
	public static final boolean CHECK_POINTER_ALIGNMENT = !true;

	private static final boolean manually_fill_and_copy = true;
	static final List<ByteBuffer> immortable_buffers = new ArrayList<>();

	public static int allocate(int sizeof) {
		return allocate(sizeof, StructThreadLocalStack.get());
	}

	public static int[] allocateArray(int length, int sizeof) {
		return allocateArray(length, sizeof, StructThreadLocalStack.get());
	}

	// ---

	public static int allocate(int sizeof, StructAllocationStack stack) {
		int handle = stack.allocate(sizeof);

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

	public static int allocateSkipZeroFill(int sizeof, StructAllocationStack stack) {
		return stack.allocate(sizeof);
	}

	public static int allocateCopy(int srcHandle, int sizeof) {
		int dstHandle = StructThreadLocalStack.get().allocate(sizeof);

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

	public static int[] allocateArray(int length, int sizeof, StructAllocationStack stack) {
		int handle = stack.allocate(sizeof * length);
		fillMemoryByWord(handle, sizeof * length, 0x00000000);
		int[] arr = new int[length];
		for(int i = 0; i < arr.length; i++)
			arr[i] = handle + i;
		return arr;
	}

	public static int[] mapArray(ByteBuffer bb, int sizeof) {
		long addr = StructUnsafe.getBufferBaseAddress(bb) + bb.position();
		int count = bb.remaining() / sizeof;
		if(count == 0)
			throw new IllegalStateException("no usable space in buffer");
		int handle = pointer2handle(addr);
		fillMemoryByWord(handle, sizeof * count, 0x00000000);
		int[] arr = new int[count];
		for(int i = 0; i < arr.length; i++)
			arr[i] = handle + i;
		return arr;
	}

	public static String toString(int handle) {
		return "<struct@" + handle2pointer(handle) + ">";
	}

	public static long alignBufferToWord(ByteBuffer bb) {
		long addr = StructUnsafe.getBufferBaseAddress(bb) + bb.position();
		int error = (int) (addr & 0x3);
		if(error != 0) {
			int advance = 4 - error;
			bb.position(bb.position() + advance);
			addr += advance;
		}
		return addr;
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
		return StructThreadLocalStack.get().isOnStack(handle);
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
		if(CHECK_POINTER_ALIGNMENT)
			if(pointer < 0L || (pointer & 3) != 0)
				throw new IllegalStateException("pointer must be 32-bit aligned");
		long handle = pointer >> 2;
		if(handle > 0xFFFF_FFFFL)
			throw new IllegalStateException("pointer too big to fit in compressed pointer (addressable memory is 16 GB)");
		return (int) handle;
	}

	//private static final float[] backing_memory = new float[256 * 1024];

	public static final void write(int handle, float value, int fieldOffset) {
		if(CHECK_MEMORY_ACCESS_REGION) {
			StructAllocationStack stack = StructThreadLocalStack.get();
			if(stack.isOnBlock(handle) && !stack.isOnStack(handle)) {
				throw new IllegalStackAccessError();
			}
		}
		//backing_memory[handle + fieldOffset] = value;
		StructUnsafe.UNSAFE.putFloat(handle2pointer(handle) + fieldOffset, value);
	}

	public static final float read(int handle, int fieldOffset) {
		if(CHECK_MEMORY_ACCESS_REGION) {
			StructAllocationStack stack = StructThreadLocalStack.get();
			if(stack.isOnBlock(handle) && !stack.isOnStack(handle)) {
				throw new IllegalStackAccessError();
			}
		}
		//return backing_memory[handle + fieldOffset];
		return StructUnsafe.UNSAFE.getFloat(handle2pointer(handle) + fieldOffset);
	}
}
