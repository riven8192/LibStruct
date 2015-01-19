package net.indiespot.struct.codegen.templates;

import net.indiespot.struct.cp.Struct;
import net.indiespot.struct.cp.TakeStruct;
import net.indiespot.struct.transform.StructEnv;

public class Block<T> {
	private final int length;
	private final T base;

	private int size;

	public Block(int length) {
		this.length = length;
		this.base = Struct.malloc((Class<T>)Object.class, length)[0];
		this.size = 0;
	}

	public void clear() {
		size = 0;
	}

	public int size() {
		return size;
	}

	@TakeStruct
	public T add() {
		return this.get(size++);
	}

	@TakeStruct
	public T add(T src) {
		T dst = this.add();
		Struct.copy((Class<T>)Object.class, src, dst);
		return dst;
	}

	public void addAll(Block<T> src) {
		if (StructEnv.SAFETY_FIRST)
			if (src.size > this.length - this.size)
				throw new IllegalStateException();
		Struct.copy((Class<T>)Object.class, src.base, this.get(this.size), src.size);
		this.size += src.size;
	}

	@TakeStruct
	public T get(int index) {
		if (StructEnv.SAFETY_FIRST)
			if (index < 0 || index >= size)
				throw new IllegalStateException();
		return Struct.sibling(base, (Class<T>)Object.class, index);
	}

	public void set(int index, T value) {
		Struct.copy((Class<T>)Object.class, value, this.get(index));
	}

	public void free() {
		T[] arr = Struct.emptyArray((Class<T>)Object.class, length);
		for (int i = 0; i < length; i++)
			arr[i] = this.get(i);
		Struct.free(arr);
	}
}