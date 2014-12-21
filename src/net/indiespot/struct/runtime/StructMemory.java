package net.indiespot.struct.runtime;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import net.indiespot.struct.transform.StructEnv;

public class StructMemory {
	public static final boolean CHECK_ALLOC_OVERFLOW = StructEnv.SAFETY_FIRST || false;
	public static final boolean CHECK_MEMORY_ACCESS_REGION = StructEnv.SAFETY_FIRST || false;
	public static final boolean CHECK_POINTER_ALIGNMENT = StructEnv.SAFETY_FIRST || false;
	public static final boolean CHECK_FIELD_ASSIGNMENT = StructEnv.SAFETY_FIRST || false;
	public static final boolean CHECK_SOURCECODE = StructEnv.SAFETY_FIRST || true;

	private static final boolean manually_fill_and_copy = true;

	public static int[] emptyArray(int length) {
		return new int[length];
	}

	// ---

	public static int allocate(int sizeof, StructAllocationStack stack) {
		int handle = stack.allocate(sizeof);

		if (manually_fill_and_copy) {
			fillMemoryByWord(handle, bytes2words(sizeof), 0x00000000);
		} else {
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
		int dstHandle = StructThreadLocalStack.getStack().allocate(sizeof);

		if (manually_fill_and_copy) {
			copyMemoryByWord(srcHandle, dstHandle, bytes2words(sizeof));
		} else {
			StructUnsafe.UNSAFE.copyMemory(//
					handle2pointer(srcHandle),//
					handle2pointer(dstHandle),//
					sizeof);
		}

		return dstHandle;
	}

	public static int[] allocateArray(int length, int sizeof, StructAllocationStack stack) {
		int sizeofWords = bytes2words(sizeof);
		int handle = stack.allocate(sizeof * length);
		fillMemoryByWord(handle, sizeofWords * length, 0x00000000);
		int[] arr = new int[length];
		for (int i = 0; i < arr.length; i++)
			arr[i] = handle + i * sizeofWords;
		return arr;
	}

	public static int[] mapBuffer(int sizeof, ByteBuffer bb) {
		int sizeofWords = bytes2words(sizeof);
		long addr = StructUnsafe.getBufferBaseAddress(bb) + bb.position();
		int count = bb.remaining() / sizeof;
		if (count == 0)
			throw new IllegalStateException("no usable space in buffer");
		int handle = pointer2handle(addr);
		int[] arr = new int[count];
		for (int i = 0; i < arr.length; i++)
			arr[i] = handle + i * sizeofWords;
		return arr;
	}

	public static int[] mapBuffer(int sizeof, ByteBuffer bb, int stride, int offset) {
		if (offset < 0 || offset + sizeof > stride || (offset % 4) != 0 || (stride % 4) != 0)
			throw new IllegalStateException();

		int offsetWords = bytes2words(offset);
		int strideWords = bytes2words(stride);
		long addr = StructUnsafe.getBufferBaseAddress(bb) + bb.position();
		int count = bb.remaining() / stride;
		if (count == 0)
			throw new IllegalStateException("no usable space in buffer");
		int handle = pointer2handle(addr);
		int[] arr = new int[count];
		for (int i = 0; i < arr.length; i++)
			arr[i] = handle + offsetWords + i * strideWords;
		return arr;
	}

	public static void copy(int sizeof, int srcHandle, int dstHandle) {
		copyMemoryByWord(srcHandle, dstHandle, bytes2words(sizeof));
	}

	public static void swap(int sizeof, int srcHandle, int dstHandle) {
		swapMemoryByWord(srcHandle, dstHandle, bytes2words(sizeof));
	}

	public static int view(int srcHandle, int offset) {
		return srcHandle + bytes2words(offset);
	}

	public static int sibling(int srcHandle, int sizeof, int count) {
		return srcHandle + bytes2words(sizeof) * count;
	}

	public static String toString(int handle) {
		return "<struct@" + handle2pointer(handle) + ">";
	}

	public static long alignBufferToWord(ByteBuffer bb) {
		long addr = StructUnsafe.getBufferBaseAddress(bb) + bb.position();
		int error = (int) (addr & (4 - 1));
		if (error != 0) {
			int advance = 4 - error;
			bb.position(bb.position() + advance);
			addr += advance;
		}
		return addr;
	}

	public static long alignBuffer(ByteBuffer bb, int alignment) {
		long addr = StructUnsafe.getBufferBaseAddress(bb) + bb.position();
		int error = (int) (addr % alignment);
		if (error != 0) {
			int advance = alignment - error;
			bb.position(bb.position() + advance);
			addr += advance;
		}
		return addr;
	}

	public static final void clearMemory(int handle, int sizeof) {
		fillMemoryByWord(handle, bytes2words(sizeof), 0x00000000);
	}

	private static final void fillMemoryByWord(int handle, int count, int value) {
		long p = handle2pointer(handle);
		for (int i = 0; i < count; i++) {
			int off = (i << 2);
			StructUnsafe.UNSAFE.putInt(p + off, value);
		}
	}

	private static final void copyMemoryByWord(int src, int dst, int count) {
		long pSrc = handle2pointer(src);
		long pDst = handle2pointer(dst);
		for (int i = 0; i < count; i++) {
			int off = (i << 2);
			StructUnsafe.UNSAFE.putInt(pDst + off, StructUnsafe.UNSAFE.getInt(pSrc + off));
		}
	}

	private static final void swapMemoryByWord(int src, int dst, int count) {
		long pSrc = handle2pointer(src);
		long pDst = handle2pointer(dst);
		for (int i = 0; i < count; i++) {
			int off = (i << 2);
			int t = StructUnsafe.UNSAFE.getInt(pSrc + off);
			StructUnsafe.UNSAFE.putInt(pSrc + off, StructUnsafe.UNSAFE.getInt(pDst + off));
			StructUnsafe.UNSAFE.putInt(pDst + off, t);
		}
	}

	public static boolean isValid(int handle) {
		return StructThreadLocalStack.getStack().isOnStack(handle);
	}

	public static void execCheckcastInsn() {
		throw new ClassCastException("cannot cast structs to a class");
	}

	public static int bytes2words(long sizeof) {
		return (int) (sizeof >> 2);
	}

	public static int bytes2words(int sizeof) {
		return sizeof >> 2;
	}

	public static long handle2pointer(int handle) {
		return (handle & 0xFFFF_FFFFL) << 2;
	}

	public static int pointer2handle(long pointer) {
		if (CHECK_POINTER_ALIGNMENT)
			if (pointer < 0L || (pointer & 3) != 0)
				throw new IllegalStateException("pointer must be 32-bit aligned");
		long handle = pointer >> 2;
		if (handle > 0xFFFF_FFFFL)
			throw new IllegalStateException("pointer too big to fit in compressed pointer (addressable memory is 16 GB)");
		return (int) handle;
	}

	private static void checkHandle(int handle) {
		if (CHECK_MEMORY_ACCESS_REGION) {
			if (handle == 0)
				throw new NullPointerException("null struct");
			StructAllocationStack stack = StructThreadLocalStack.getStack();
			if (stack.isOnBlock(handle) && !stack.isOnStack(handle)) {
				throw new IllegalStackAccessError();
			}
		}
	}

	public static void checkFieldAssignment(int handle) {
		if (handle == 0)
			throw new NullPointerException("null struct");
		StructAllocationStack stack = StructThreadLocalStack.getStack();
		if (stack.isOnBlock(handle) && stack.isOnStack(handle)) {
			throw new SuspiciousFieldAssignmentError();
		}
	}

	public static void checkFieldAssignment(int targetHandle, int handle) {
		if (handle == 0)
			throw new NullPointerException("null struct");
		if (targetHandle == 0)
			throw new NullPointerException("null struct");
		StructAllocationStack stack = StructThreadLocalStack.getStack();
		if (stack.isOnBlock(handle) && stack.isOnStack(handle)) {
			if (!stack.isOnBlock(targetHandle) || !stack.isOnStack(targetHandle)) {
				throw new SuspiciousFieldAssignmentError();
			}
		}
	}

	static class Holder {
		private final ByteBuffer buffer;
		private final StructAllocationStack stack;

		public Holder(ByteBuffer buffer, StructAllocationStack stack) {
			this.buffer = buffer;
			this.stack = stack;
		}

		public ByteBuffer buffer() {
			return buffer;
		}

		public StructAllocationStack stack() {
			return stack;
		}
	}

	private static final List<Holder> immortal = new ArrayList<>();

	public static StructAllocationStack createStructAllocationStack(int bytes) {
		ByteBuffer buffer = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder());
		long addr = StructMemory.alignBufferToWord(buffer);
		int handleOffset = StructMemory.pointer2handle(addr);
		StructAllocationStack stack = new StructAllocationStack(handleOffset, buffer.remaining());
		synchronized (immortal) {
			immortal.add(new Holder(buffer, stack));
		}
		return stack;
	}

	public static void discardStructAllocationStack(StructAllocationStack stack) {
		synchronized (immortal) {
			for (int i = 0, len = immortal.size(); i < len; i++) {
				if (immortal.get(i).stack == stack) {
					immortal.remove(i);
					return;
				}
			}
		}

		throw new NoSuchElementException();
	}

	// boolean (not supported by Unsafe, so we piggyback on putByte, getByte)

	public static final void zput(int handle, boolean value, int fieldOffset) {
		bput(handle, value ? (byte) 0x01 : (byte) 0x00, fieldOffset);
	}

	public static final boolean zget(int handle, int fieldOffset) {
		return bget(handle, fieldOffset) == (byte) 0x01;
	}

	// byte

	public static final void bput(int handle, byte value, int fieldOffset) {
		checkHandle(handle);
		StructUnsafe.UNSAFE.putByte(handle2pointer(handle) + fieldOffset, value);
	}

	public static final byte bget(int handle, int fieldOffset) {
		checkHandle(handle);
		return StructUnsafe.UNSAFE.getByte(handle2pointer(handle) + fieldOffset);
	}

	// short

	public static final void sput(int handle, short value, int fieldOffset) {
		checkHandle(handle);
		StructUnsafe.UNSAFE.putShort(handle2pointer(handle) + fieldOffset, value);
	}

	public static final short sget(int handle, int fieldOffset) {
		checkHandle(handle);
		return StructUnsafe.UNSAFE.getShort(handle2pointer(handle) + fieldOffset);
	}

	// char

	public static final void cput(int handle, char value, int fieldOffset) {
		checkHandle(handle);
		StructUnsafe.UNSAFE.putChar(handle2pointer(handle) + fieldOffset, value);
	}

	public static final char cget(int handle, int fieldOffset) {
		checkHandle(handle);
		return StructUnsafe.UNSAFE.getChar(handle2pointer(handle) + fieldOffset);
	}

	// int

	public static final void iput(int handle, int value, int fieldOffset) {
		checkHandle(handle);
		StructUnsafe.UNSAFE.putInt(handle2pointer(handle) + fieldOffset, value);
	}

	public static final int iget(int handle, int fieldOffset) {
		checkHandle(handle);
		return StructUnsafe.UNSAFE.getInt(handle2pointer(handle) + fieldOffset);
	}

	// float

	public static final void fput(int handle, float value, int fieldOffset) {
		checkHandle(handle);
		StructUnsafe.UNSAFE.putFloat(handle2pointer(handle) + fieldOffset, value);
	}

	public static final float fget(int handle, int fieldOffset) {
		checkHandle(handle);
		return StructUnsafe.UNSAFE.getFloat(handle2pointer(handle) + fieldOffset);
	}

	// long

	public static final void jput(int handle, long value, int fieldOffset) {
		checkHandle(handle);
		StructUnsafe.UNSAFE.putLong(handle2pointer(handle) + fieldOffset, value);
	}

	public static final long jget(int handle, int fieldOffset) {
		checkHandle(handle);
		return StructUnsafe.UNSAFE.getLong(handle2pointer(handle) + fieldOffset);
	}

	// double

	public static final void dput(int handle, double value, int fieldOffset) {
		checkHandle(handle);
		StructUnsafe.UNSAFE.putDouble(handle2pointer(handle) + fieldOffset, value);
	}

	public static final double dget(int handle, int fieldOffset) {
		checkHandle(handle);
		return StructUnsafe.UNSAFE.getDouble(handle2pointer(handle) + fieldOffset);
	}

	// struct

	public static final void $put(int handle, int value, int fieldOffset) {
		checkHandle(handle);
		StructUnsafe.UNSAFE.putInt(handle2pointer(handle) + fieldOffset, value);
	}

	public static final int $get(int handle, int fieldOffset) {
		checkHandle(handle);
		return StructUnsafe.UNSAFE.getInt(handle2pointer(handle) + fieldOffset);
	}

	// boolean[]

	public static final void zaput(int arrayHandle, int index, boolean value) {
		zput(arrayHandle, value, (index << 0));
	}

	public static final boolean zaget(int arrayHandle, int index) {
		return zget(arrayHandle, (index << 0));
	}

	// byte[]

	public static final void baput(int arrayHandle, int index, byte value) {
		bput(arrayHandle, value, (index << 0));
	}

	public static final byte baget(int arrayHandle, int index) {
		return bget(arrayHandle, (index << 0));
	}

	// short[]

	public static final void saput(int arrayHandle, int index, short value) {
		sput(arrayHandle, value, (index << 1));
	}

	public static final short saget(int arrayHandle, int index) {
		return sget(arrayHandle, (index << 1));
	}

	// char[]

	public static final void caput(int arrayHandle, int index, char value) {
		cput(arrayHandle, value, (index << 1));
	}

	public static final char caget(int arrayHandle, int index) {
		return cget(arrayHandle, (index << 1));
	}

	// int[]

	public static final void iaput(int arrayHandle, int index, int value) {
		iput(arrayHandle, value, (index << 2));
	}

	public static final int iaget(int arrayHandle, int index) {
		return iget(arrayHandle, (index << 2));
	}

	// float[]

	public static final void faput(int arrayHandle, int index, float value) {
		fput(arrayHandle, value, (index << 2));
	}

	public static final float faget(int arrayHandle, int index) {
		return fget(arrayHandle, (index << 2));
	}

	// long[]

	public static final void japut(int arrayHandle, int index, long value) {
		jput(arrayHandle, value, (index << 3));
	}

	public static final long jaget(int arrayHandle, int index) {
		return jget(arrayHandle, (index << 3));
	}

	// double[]

	public static final void daput(int arrayHandle, int index, double value) {
		dput(arrayHandle, value, (index << 3));
	}

	public static final double daget(int arrayHandle, int index) {
		return dget(arrayHandle, (index << 3));
	}
}
