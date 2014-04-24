package net.indiespot.struct.runtime;

import java.nio.ByteBuffer;

public class StructUtil {
	public static long getPointer(Object struct) {
		throwFit();
		return 0L;
	}

	public static boolean isReachable(Object struct) {
		throwFit();
		return false;
	}

	public static <T> T[] map(Class<T> structType, ByteBuffer bb) {
		throwFit();
		return null;
	}

	private static void throwFit() {
		throw new UnsupportedOperationException("callsite was not transformed by struct library");
	}
}
