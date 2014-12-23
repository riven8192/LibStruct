package net.indiespot.struct.cp;

import java.nio.ByteBuffer;

import net.indiespot.struct.runtime.StructAllocationStack;

public class Struct {
	public static <T> T typedNull(Class<T> structType) {
		throwFit();
		return null;
	}

	public static <T> T[] emptyArray(Class<T> structType, int length) {
		throwFit();
		return null;
	}

	//

	public static <T> int sizeof(Class<T> structType) {
		throwFit();
		return 0;
	}

	//

	public static <T> T malloc(Class<T> structType) {
		throwFit();
		return null;
	}

	public static <T> T calloc(Class<T> structType) {
		throwFit();
		return null;
	}

	public static <T> T[] malloc(Class<T> structType, int length) {
		throwFit();
		return null;
	}

	public static <T> T[] calloc(Class<T> structType, int length) {
		throwFit();
		return null;
	}

	public static <T> T[] realloc(Class<T> structType, T[] currentArray, int newLength) {
		throwFit();
		return null;
	}

	public static <T> void free(T struct) {
		throwFit();
	}

	public static <T> void free(T[] structArray) {
		throwFit();
	}

	//

	public static <T> void copy(Class<T> structType, T src, T dst) {
		throwFit();
	}

	public static <T> void swap(Class<T> structType, T src, T dst) {
		throwFit();
	}

	public static <T, A> A view(T struct, Class<A> asType, int offsetMultipleOf4) {
		throwFit();
		return null;
	}

	public static <T, A> A sibling(T struct, Class<A> asType, int move) {
		throwFit();
		return null;
	}

	//

	public static <T> T[] map(Class<T> structType, ByteBuffer bb) {
		throwFit();
		return null;
	}

	public static <T> T[] map(Class<T> structType, ByteBuffer bb, int stride, int offset) {
		throwFit();
		return null;
	}

	//

	public static <T> T fromPointer(long pointer) {
		throwFit();
		return null;
	}

	public static long getPointer(Object struct) {
		throwFit();
		return 0L;
	}

	public static boolean isReachable(Object struct) {
		throwFit();
		return false;
	}

	//

	public static StructAllocationStack createStructAllocationStack(int bytes) {
		throwFit();
		return null;
	}

	public static void discardStructAllocationStack(StructAllocationStack stack) {
		throwFit();
	}

	public static <T> T stackAlloc(StructAllocationStack stack, Class<T> structType) {
		throwFit();
		return null;
	}

	//

	private static void throwFit() {
		throw new UnsupportedOperationException("callsite was not transformed by struct library");
	}
}
