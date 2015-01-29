package net.indiespot.struct.runtime;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import net.indiespot.struct.transform.StructEnv;

public class StructMemory {
	public static long[] nullArray(int length) {
		return new long[length];
	}

	// ---

	public static long allocate(int sizeof, StructAllocationStack stack) {
		long handle = stack.allocate(sizeof);

		fillMemoryByWord(handle, bytes2words(sizeof), 0x00000000);

		return handle;
	}

	public static long allocateSkipZeroFill(int sizeof, StructAllocationStack stack) {
		return stack.allocate(sizeof);
	}

	public static long allocateCopy(long srcHandle, int sizeof) {
		long dstHandle = StructThreadLocalStack.getStack().allocate(sizeof);

		copyMemoryByWord(srcHandle, dstHandle, bytes2words(sizeof));

		return dstHandle;
	}

	public static long[] allocateArray(int length, int sizeof, StructAllocationStack stack) {
		int sizeofWords = bytes2words(sizeof);
		long handle = stack.allocate(sizeof * length);
		fillMemoryByWord(handle, sizeofWords * length, 0x00000000);
		return createPointerArray(handle, sizeof, length);
	}

	public static long[] mapBuffer(int sizeof, ByteBuffer bb) {
		long addr = StructUnsafe.getBufferBaseAddress(bb) + bb.position();
		int count = bb.remaining() / sizeof;
		if (count == 0)
			throw new IllegalStateException("no usable space in buffer");
		return createPointerArray(addr, sizeof, count);
	}

	public static long[] mapBuffer(int sizeof, ByteBuffer bb, int stride, int offset) {
		if (offset < 0 || offset + sizeof > stride || (offset % 4) != 0 || (stride % 4) != 0)
			throw new IllegalStateException();

		long addr = StructUnsafe.getBufferBaseAddress(bb) + bb.position() + offset;
		int count = bb.remaining() / stride;
		if (count == 0)
			throw new IllegalStateException("no usable space in buffer");
		return createPointerArray(addr, stride, count);
	}

	public static void copy(int sizeof, long srcHandle, long dstHandle) {
		copyMemoryByWord(srcHandle, dstHandle, bytes2words(sizeof));
	}

	public static void copy(int sizeof, long srcHandle, long dstHandle, int count) {
		copyMemoryByWord(srcHandle, dstHandle, bytes2words(sizeof) * count);
	}

	public static void swap(int sizeof, long srcHandle, long dstHandle) {
		swapMemoryByWord(srcHandle, dstHandle, bytes2words(sizeof));
	}

	public static long view(long srcHandle, int offset) {
		return srcHandle + offset;
	}

	public static long index(long base, int sizeof, int count) {
		return base + (long) sizeof * count;
	}

	public static String toString(long handle) {
		return "<struct@" + handle + ">";
	}

	public static long alignBufferToWord(ByteBuffer bb) {
		long addr = StructUnsafe.getBufferBaseAddress(bb) + bb.position();
		long aligned = alignAddressToWord(addr);
		if (addr != aligned)
			bb.position(bb.position() + (int) (aligned - addr));
		return addr;
	}

	public static long alignAddressToWord(long addr) {
		int error = (int) (addr & (4 - 1));
		if (error != 0)
			addr += (4 - error);
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

	public static final void clearMemory(long addr, int sizeof) {
		fillMemoryByWord(addr, bytes2words(sizeof), 0x00000000);
	}

	public static final void clearMemory(long addr, long sizeof) {
		fillMemoryByWord(addr, bytes2words(sizeof), 0x00000000);
	}

	private static final int word_count_int_shift_limit = Integer.MAX_VALUE >> 2;

	private static final void fillMemoryByWord(long addr, int wordCount, int wordValue) {
		long base = addr;

		int limit = Math.min(wordCount, word_count_int_shift_limit);
		for (int i = 0; i < limit; i++)
			StructUnsafe.UNSAFE.putInt(base + (i << 2), wordValue);
		for (int i = limit; i < wordCount; i++)
			StructUnsafe.UNSAFE.putInt(base + ((long) i << 2), wordValue);
	}

	private static final void copyMemoryByWord(long pSrc, long pDst, int wordCount) {
		int limit = Math.min(wordCount, word_count_int_shift_limit);
		for (int i = 0; i < limit; i++)
			StructUnsafe.UNSAFE.putInt(pDst + (i << 2), StructUnsafe.UNSAFE.getInt(pSrc + (i << 2)));
		for (int i = limit; i < wordCount; i++)
			StructUnsafe.UNSAFE.putInt(pDst + ((long) i << 2), StructUnsafe.UNSAFE.getInt(pSrc + ((long) i << 2)));
	}

	private static final void swapMemoryByWord(long pSrc, long pDst, int wordCount) {
		for (int i = 0; i < wordCount; i++) {
			long off = ((long) i << 2);
			int tmp = StructUnsafe.UNSAFE.getInt(pSrc + off);
			StructUnsafe.UNSAFE.putInt(pSrc + off, StructUnsafe.UNSAFE.getInt(pDst + off));
			StructUnsafe.UNSAFE.putInt(pDst + off, tmp);
		}
	}

	public static boolean isValid(long handle) {
		return StructThreadLocalStack.getStack().isOnStack(handle);
	}

	public static void execCheckcastInsn() {
		throw new ClassCastException("cannot cast structs to a class");
	}

	public static int bytes2words(long sizeof) {
		if (StructEnv.SAFETY_FIRST)
			if (sizeof < 0 || (sizeof & 0x03) != 0x00)
				throw new IllegalStateException();
		if (StructEnv.SAFETY_FIRST)
			if (sizeof > 0x2_FFFF_FFFFL)
				throw new IllegalStateException();
		int words = (int) (sizeof >> 2);
		if (StructEnv.SAFETY_FIRST)
			if (words < 0)
				throw new IllegalStateException();
		return words;
	}

	public static int bytes2words(int sizeof) {
		if (StructEnv.SAFETY_FIRST)
			if (sizeof < 0 || (sizeof & 0x03) != 0x00)
				throw new IllegalStateException();
		return sizeof >> 2;
	}

	public static long[] createPointerArray(long pointer, int sizeof, int length) {
		long[] arr = new long[length];
		for (int i = 0; i < length; i++)
			arr[i] = pointer + (long) i * sizeof;
		return arr;
	}

	private static void checkPointer(long pointer) {
		if (StructEnv.SAFETY_FIRST) {
			if (pointer == 0)
				throw new NullPointerException("null struct");
			StructAllocationStack stack = StructThreadLocalStack.getStack();
			if (stack.isOnBlock(pointer) && !stack.isOnStack(pointer)) {
				throw new IllegalStackAccessError();
			}
		}
	}

	public static void checkFieldAssignment(long handle) {
		if (handle == 0)
			throw new NullPointerException("null struct");
		StructAllocationStack stack = StructThreadLocalStack.getStack();
		if (stack.isOnBlock(handle) && stack.isOnStack(handle)) {
			throw new SuspiciousFieldAssignmentError();
		}
	}

	public static void checkFieldAssignment(long targetHandle, long handle) {
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
		StructAllocationStack stack = new StructAllocationStack(addr, buffer.remaining());
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

	public static final void zput(long addr, boolean value, int fieldOffset) {
		bput(addr, value ? (byte) 0x01 : (byte) 0x00, fieldOffset);
	}

	public static final boolean zget(long addr, int fieldOffset) {
		return bget(addr, fieldOffset) == (byte) 0x01;
	}

	// byte

	public static final void bput(long addr, byte value, int fieldOffset) {
		checkPointer(addr);
		StructUnsafe.UNSAFE.putByte(addr + fieldOffset, value);
	}

	public static final byte bget(long addr, int fieldOffset) {
		checkPointer(addr);
		return StructUnsafe.UNSAFE.getByte(addr + fieldOffset);
	}

	// short

	public static final void sput(long addr, short value, int fieldOffset) {
		checkPointer(addr);
		StructUnsafe.UNSAFE.putShort(addr + fieldOffset, value);
	}

	public static final short sget(long addr, int fieldOffset) {
		checkPointer(addr);
		return StructUnsafe.UNSAFE.getShort(addr + fieldOffset);
	}

	// char

	public static final void cput(long addr, char value, int fieldOffset) {
		checkPointer(addr);
		StructUnsafe.UNSAFE.putChar(addr + fieldOffset, value);
	}

	public static final char cget(long addr, int fieldOffset) {
		checkPointer(addr);
		return StructUnsafe.UNSAFE.getChar(addr + fieldOffset);
	}

	// int

	public static final void iput(long addr, int value, int fieldOffset) {
		checkPointer(addr);
		StructUnsafe.UNSAFE.putInt(addr + fieldOffset, value);
	}

	public static final int iget(long addr, int fieldOffset) {
		checkPointer(addr);
		return StructUnsafe.UNSAFE.getInt(addr + fieldOffset);
	}

	// float

	public static final void fput(long addr, float value, int fieldOffset) {
		checkPointer(addr);
		StructUnsafe.UNSAFE.putFloat(addr + fieldOffset, value);
	}

	public static final float fget(long addr, int fieldOffset) {
		checkPointer(addr);
		return StructUnsafe.UNSAFE.getFloat(addr + fieldOffset);
	}

	// long

	public static final void jput(long addr, long value, int fieldOffset) {
		checkPointer(addr);
		StructUnsafe.UNSAFE.putLong(addr + fieldOffset, value);
	}

	public static final long jget(long addr, int fieldOffset) {
		checkPointer(addr);
		return StructUnsafe.UNSAFE.getLong(addr + fieldOffset);
	}

	// double

	public static final void dput(long addr, double value, int fieldOffset) {
		checkPointer(addr);
		StructUnsafe.UNSAFE.putDouble(addr + fieldOffset, value);
	}

	public static final double dget(long addr, int fieldOffset) {
		checkPointer(addr);
		return StructUnsafe.UNSAFE.getDouble(addr + fieldOffset);
	}

	// struct

	public static final void $put(long addr, long value, int fieldOffset) {
		checkPointer(addr);
		StructUnsafe.UNSAFE.putLong(addr + fieldOffset, value);
	}

	public static final long $get(long addr, int fieldOffset) {
		checkPointer(addr);
		return StructUnsafe.UNSAFE.getLong(addr + fieldOffset);
	}

	// boolean[]

	public static final void zaput(long arrayHandle, int index, boolean value) {
		zput(arrayHandle, value, (index << 0));
	}

	public static final boolean zaget(long arrayHandle, int index) {
		return zget(arrayHandle, (index << 0));
	}

	// byte[]

	public static final void baput(long arrayHandle, int index, byte value) {
		bput(arrayHandle, value, (index << 0));
	}

	public static final byte baget(long arrayHandle, int index) {
		return bget(arrayHandle, (index << 0));
	}

	// short[]

	public static final void saput(long arrayHandle, int index, short value) {
		sput(arrayHandle, value, (index << 1));
	}

	public static final short saget(long arrayHandle, int index) {
		return sget(arrayHandle, (index << 1));
	}

	// char[]

	public static final void caput(long arrayHandle, int index, char value) {
		cput(arrayHandle, value, (index << 1));
	}

	public static final char caget(long arrayHandle, int index) {
		return cget(arrayHandle, (index << 1));
	}

	// int[]

	public static final void iaput(long arrayHandle, int index, int value) {
		iput(arrayHandle, value, (index << 2));
	}

	public static final int iaget(long arrayHandle, int index) {
		return iget(arrayHandle, (index << 2));
	}

	// float[]

	public static final void faput(long arrayHandle, int index, float value) {
		fput(arrayHandle, value, (index << 2));
	}

	public static final float faget(long arrayHandle, int index) {
		return fget(arrayHandle, (index << 2));
	}

	// long[]

	public static final void japut(long arrayHandle, int index, long value) {
		jput(arrayHandle, value, (index << 3));
	}

	public static final long jaget(long arrayHandle, int index) {
		return jget(arrayHandle, (index << 3));
	}

	// double[]

	public static final void daput(long arrayHandle, int index, double value) {
		dput(arrayHandle, value, (index << 3));
	}

	public static final double daget(long arrayHandle, int index) {
		return dget(arrayHandle, (index << 3));
	}
}
