package net.indiespot.struct.cp;

import java.nio.ByteBuffer;

import net.indiespot.struct.runtime.StructAllocationStack;

public class Struct {
	/**
	 * Returns a null reference of any struct type.
	 * 
	 * <pre>
	 * Point p1 = null; // poorly supported due to technical issues
	 * Point p2 = Struct.nullStruct(Point.class); // guaranteed to work
	 * </pre>
	 */
	public static <T> T nullStruct(Class<T> structType) {
		throwFit();
		return null;
	}

	/**
	 * Creates an array with the specified type and length, filled with null
	 * references.
	 */
	public static <T> T[] nullArray(Class<T> structType, int length) {
		throwFit();
		return null;
	}

	/**
	 * Returns the size of this struct in bytes. Note that there isn't any
	 * performance overhead, at runtime this method call is replaced with a
	 * constant integer.
	 * 
	 * <pre>
	 * int s1 = Struct.sizeof(Point.class);
	 * int s1 = 8;
	 * </pre>
	 */

	public static <T> int sizeof(Class<T> structType) {
		throwFit();
		return 0;
	}

	/**
	 * Allocates a struct on the LibStruct heap, of which the memory will
	 * contain garbage.
	 */

	public static <T> T malloc(Class<T> structType) {
		throwFit();
		return null;
	}

	/**
	 * Operates like <code>Struct.malloc(type)</code>, but has its backing
	 * memory cleared.
	 */

	public static <T> T calloc(Class<T> structType) {
		throwFit();
		return null;
	}

	/**
	 * Allocates a struct-array on the LibStruct heap, of which the memory will
	 * contain garbage. The structs in the array are in a contiguous block of
	 * memory.
	 */

	public static <T> T[] malloc(Class<T> structType, int length) {
		throwFit();
		return null;
	}

	/**
	 * Operates like <code>Struct.malloc(type,length)</code>, but has its
	 * backing memory cleared.
	 */

	public static <T> T[] calloc(Class<T> structType, int length) {
		throwFit();
		return null;
	}

	/**
	 * Frees every struct in the passed struct-array and allocates a new
	 * struct-array on the LibStruct heap, of which the initial range of memory
	 * will contain copied data from the input array, and will contain garbage
	 * for the extended range, if any. The structs in the output-array are in a
	 * contiguous block of memory. The structs in the input-array may be
	 * scattered.
	 */

	public static <T> T[] realloc(Class<T> structType, T[] currentArray, int newLength) {
		throwFit();
		return null;
	}

	/**
	 * Operates like <code>Struct.malloc(type,length)</code>, but only returns
	 * the first struct in the array. This first element may be freed, the other
	 * structs in this block of memory must not be freed. Lookup elements in the
	 * memory block by using <code>Struct.index(base, type, index)</code>
	 * 
	 * <pre>
	 * int len = 23;
	 * Point base = Struct.mallocBlock(Point.class, len);
	 * for (int i = 0; i &lt; len; i++) {
	 * 	Point p = Struct.index(base, Point.class, i);
	 * }
	 * Struct.free(base);
	 * </pre>
	 */

	public static <T> T mallocBlock(Class<T> structType, int length) {
		throwFit();
		return null;
	}

	/**
	 * Frees the memory in use by this struct. The following will corrupt
	 * LibStruct memory management:
	 * <ul>
	 * <li>freeing a struct that was not heap-allocated (meaning:
	 * stack-allocated, a reference derived from
	 * <code>Struct.view(base, type, offset)</code>,
	 * <code>Struct.index(base, type, index)</code>,
	 * <code>Struct.map(type, buffer)</code> or
	 * <code>Struct.fromPointer(address)</code>)</li>
	 * <li>freeing an heap-allocated struct more than once</li>
	 * <li>freeing a null reference</li>
	 * </ul>
	 */

	public static <T> void free(T struct) {
		throwFit();
	}

	/**
	 * Operates like <code>Struct.free(struct)</code> for every struct in the
	 * array. The structs in this array may be scattered over the heap.
	 */
	public static <T> void free(T[] structArray) {
		throwFit();
	}

	/**
	 * Copies the bytes of the memory used by <code>src</code> into the memory
	 * used by <code>dst</code>.
	 */

	public static <T> void copy(Class<T> structType, T src, T dst) {
		throwFit();
	}

	/**
	 * Operates like <code>Struct.copy(type, src, dst)</code>, but copies a
	 * block of memory with size <code>sizeof(type)*count</code>.
	 */

	public static <T> void copy(Class<T> structType, T src, T dst, int count) {
		throwFit();
	}

	/**
	 * Swaps the bytes of the memory used by <code>src</code> with the memory
	 * used by <code>dst</code>.
	 */
	public static <T> void swap(Class<T> structType, T src, T dst) {
		throwFit();
	}

	/**
	 * Reinterpret the memory relative to a struct, as another struct of any
	 * type. Typically used to <i>dynamically</i> embed structs into structs.
	 * For <i>statically</i> embedded structs into structs, use:
	 * <code>class Ship { @StructField(embed=true) Point position; }</code> 
	 * 
	 * <pre>
	 * Ship ship = new Ship();
	 * Point position = Struct.view(ship, Point.class, offset);
	 * Point position = Struct.fromPointer(Struct.getPointer(ship) + offset));
	 * </pre>
	 */

	public static <T, A> A view(T struct, Class<A> asType, int offsetMultipleOf4) {
		throwFit();
		return null;
	}

	/**
	 * Reinterpret the memory relative to the specified base struct, as another
	 * struct of the same type, where the <code>index</code> parameter is used
	 * to calculate the offset: <code>sizeof(type)*index</code>. This can be
	 * used to reference structs in a block of memory returned by
	 * <code>Struct.mallocBlock(type, length)</code>. *
	 * 
	 * <pre>
	 * int len = 23;
	 * Point base = Struct.mallocBlock(Point.class, len);
	 * for (int i = 0; i &lt; len; i++) {
	 * 	Point p = Struct.index(base, Point.class, i);
	 * }
	 * Struct.free(base);
	 * </pre>
	 */

	public static <T> T index(T base, Class<T> asType, int index) {
		throwFit();
		return null;
	}

	/**
	 * Creates a struct array of which the elements are backed by the memory in
	 * the buffer, with a stride of <code>Struct.sizeof(type)</code>. Keep a
	 * strong reference to this buffer, to prevent accessing the returned
	 * structs when the Java GC may have reclaimed the backing memory.
	 */

	public static <T> T[] map(Class<T> structType, ByteBuffer bb) {
		throwFit();
		return null;
	}

	/**
	 * Operates like <code>Struct.map(type, buffer)</code>, except it uses the
	 * specified offset and stride.
	 */

	public static <T> T[] map(Class<T> structType, ByteBuffer bb, int stride, int offset) {
		throwFit();
		return null;
	}

	/**
	 * Reinterpret the memory at the specified address as a struct of any type.
	 */

	public static <T> T fromPointer(long pointer) {
		throwFit();
		return null;
	}

	/**
	 * Get the pointer of the specified struct.
	 */

	public static <T> long getPointer(T struct) {
		throwFit();
		return 0L;
	}

	/**
	 * Tests whether a stack allocated struct reference is still valid to
	 * reference at the callsite of this method. For debugging purposes.
	 */

	public static <T> boolean isReachable(T struct) {
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
