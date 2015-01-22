package net.indiespot.struct.codegen.templates;

import net.indiespot.struct.cp.Struct;
import net.indiespot.struct.cp.TakeStruct;
import net.indiespot.struct.transform.StructEnv;

public class Block<T> {
	private final int cap;
	private final T base;

	private int size;

	public Block(int cap) {
		if (cap <= 0)
			throw new IllegalArgumentException();
		this.cap = cap;
		this.base = Struct.mallocArrayBase((Class<T>) Object.class, cap);
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
		Struct.copy((Class<T>) Object.class, src, dst);
		return dst;
	}

	public void addRange(Block<T> src, int off, int len) {
		if (StructEnv.SAFETY_FIRST)
			if (off < 0 || len < 0 || off + len > src.size)
				throw new IllegalStateException();
		if (StructEnv.SAFETY_FIRST)
			if (len > this.cap - this.size)
				throw new IllegalStateException();
		int offset = this.size;
		this.size += len;
		Struct.copy((Class<T>) Object.class, src.get(off), this.get(offset), len);
	}

	public void addAll(Block<T> src) {
		if (StructEnv.SAFETY_FIRST)
			if (src.size > this.cap - this.size)
				throw new IllegalStateException();
		int offset = this.size;
		this.size += src.size;
		Struct.copy((Class<T>) Object.class, src.base, this.get(offset), src.size);
	}

	@TakeStruct
	public T get(int index) {
		if (StructEnv.SAFETY_FIRST)
			if (index < 0 || index >= size)
				throw new IllegalStateException();
		return Struct.index(base, (Class<T>) Object.class, index);
	}

	public void set(int index, T value) {
		Struct.copy((Class<T>) Object.class, value, this.get(index));
	}

	public void free() {
		Struct.free(base);
	}
}