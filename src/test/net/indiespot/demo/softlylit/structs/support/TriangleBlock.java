package test.net.indiespot.demo.softlylit.structs.support;

import test.net.indiespot.demo.softlylit.structs.Triangle;

import net.indiespot.struct.cp.Struct;
import net.indiespot.struct.cp.TakeStruct;
import net.indiespot.struct.transform.StructEnv;

public class TriangleBlock {
	private final int length;
	private final Triangle base;

	private int size;

	public TriangleBlock(int length) {
		this.length = length;
		this.base = Struct.malloc(Triangle.class, length)[0];
		this.size = 0;
	}

	public void clear() {
		size = 0;
	}

	public int size() {
		return size;
	}

	@TakeStruct
	public Triangle add() {
		return this.get(size++);
	}

	@TakeStruct
	public Triangle add(Triangle src) {
		Triangle dst = this.add();
		Struct.copy(Triangle.class, src, dst);
		return dst;
	}

	public void addAll(TriangleBlock src) {
		if (StructEnv.SAFETY_FIRST)
			if (src.size > this.length - this.size)
				throw new IllegalStateException();
		Struct.copy(Triangle.class, src.base, this.get(this.size), src.size);
		this.size += src.size;
	}

	@TakeStruct
	public Triangle get(int index) {
		if (StructEnv.SAFETY_FIRST)
			if (index < 0 || index >= size)
				throw new IllegalStateException();
		return Struct.sibling(base, Triangle.class, index);
	}

	public void set(int index, Triangle value) {
		Struct.copy(Triangle.class, value, this.get(index));
	}

	public void free() {
		Triangle[] arr = Struct.emptyArray(Triangle.class, length);
		for (int i = 0; i < length; i++)
			arr[i] = this.get(i);
		Struct.free(arr);
	}
}