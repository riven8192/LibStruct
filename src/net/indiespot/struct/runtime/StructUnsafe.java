package net.indiespot.struct.runtime;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import net.indiespot.struct.transform.StructEnv;
import sun.misc.Unsafe;

/**
 * [INTERNAL USE ONLY]
 * 
 * @author Riven
 */
@SuppressWarnings("restriction")
public class StructUnsafe {
	public static final Unsafe UNSAFE = getUnsafeInstance();

	private static final long BUFFER_ADDRESS_OFFSET = getObjectFieldOffset(ByteBuffer.class, "address");
	private static final long BUFFER_CAPACITY_OFFSET = getObjectFieldOffset(ByteBuffer.class, "capacity");

	public static long getBufferBaseAddress(ByteBuffer buffer) {
		if (buffer == null)
			throw new NullPointerException();
		if (!buffer.isDirect())
			throw new IllegalStateException();
		long addr = UNSAFE.getLong(buffer, BUFFER_ADDRESS_OFFSET);
		return addr;
	}

	private static final ByteBuffer global;
	public static final long memory_base_offset_in_words;

	static {
		global = ByteBuffer.allocateDirect(4 * 1024);
		global.order(ByteOrder.nativeOrder());
		if (StructEnv.MEMORY_BASE_OFFSET) {
			memory_base_offset_in_words = (getBufferBaseAddress(global) - (1024L * 1024L * 1024L)) >> 2;
		} else {
			memory_base_offset_in_words = 0L;
		}
	}

	public static ByteBuffer newBuffer(int capacity) {
		ByteBuffer buffer = ByteBuffer.allocateDirect(capacity);
		buffer.order(ByteOrder.nativeOrder());
		return buffer;
	}

	public static ByteBuffer newBuffer(long address, int capacity) {
		if (address <= 0L || capacity < 0)
			throw new IllegalStateException("you almost crashed the jvm");

		ByteBuffer buffer = global.duplicate();
		UNSAFE.putLong(buffer, BUFFER_ADDRESS_OFFSET, address);
		UNSAFE.putInt(buffer, BUFFER_CAPACITY_OFFSET, capacity);
		buffer.position(0);
		buffer.limit(capacity);

		return buffer;
	}

	//

	private static long getObjectFieldOffset(Class<?> type, String fieldName) {
		while (type != null) {
			try {
				return UNSAFE.objectFieldOffset(type.getDeclaredField(fieldName));
			} catch (Throwable t) {
				type = type.getSuperclass();
			}
		}
		throw new InternalError("getObjectFieldOffset[" + type + "." + fieldName + "]");
	}

	private static Unsafe getUnsafeInstance() {
		try {
			Class<?> type = Class.forName("sun.misc.Unsafe");

			for (Field field : type.getDeclaredFields()) {
				if (field.getType() == Unsafe.class) {
					field.setAccessible(true);
					return (Unsafe) field.get(null);
				}
			}
			throw new IllegalStateException();
		} catch (Exception exc) {
			throw new InternalError(exc.getClass().getName() + ": " + exc.getMessage());
		}
	}
}