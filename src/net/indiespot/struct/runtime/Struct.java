package net.indiespot.struct.runtime;

import java.nio.ByteBuffer;

public class Struct {
	public static <T> T asNull(Class<T> structType) {
		throwFit();
		return null;
	}

	public static <T> int sizeof(Class<T> structType) {
		throwFit();
		return 0;
	}

	//

	public static <T> T malloc(Class<T> structType) {
		throwFit();
		return null;
	}

	public static void free(Object struct) {
		throwFit();
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

	public static long getPointer(Object struct) {
		throwFit();
		return 0L;
	}

	public static boolean isReachable(Object struct) {
		throwFit();
		return false;
	}

	//

	private static void throwFit() {
		throw new UnsupportedOperationException("callsite was not transformed by struct library");
	}
}
