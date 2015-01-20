package net.indiespot.struct.codegen.templates;

import net.indiespot.struct.cp.Struct;
import net.indiespot.struct.cp.TakeStruct;
import net.indiespot.struct.transform.StructEnv;

public class List<T> {
	private T[] arr;
	private int size, cap;

	public List() {
		this(10);
	}

	public List(int cap) {
		if (cap <= 0)
			throw new IllegalArgumentException();
		this.expandTo(cap);
	}

	public void clear() {
		size = 0;
	}

	public void add(T elem) {
		if (size == cap)
			this.expandTo(-1);
		arr[size++] = elem;
	}

	public void addRange(List<T> list, int off, int len) {
		if (StructEnv.SAFETY_FIRST)
			if (len < 0)
				throw new IllegalArgumentException();
		if (StructEnv.SAFETY_FIRST)
			if (off + len > list.size)
				throw new IllegalArgumentException();
		for (int i = 0; i < len; i++) {
			this.add(list.arr[off + i]);
		}
	}

	public void addAll(List<T> list) {
		for (int i = 0, len = list.size; i < len; i++) {
			this.add(list.arr[i]);
		}
	}

	@TakeStruct
	public T get(int index) {
		if (StructEnv.SAFETY_FIRST)
			if (index < 0 || index >= size)
				throw new ArrayIndexOutOfBoundsException(index);
		return arr[index];
	}

	@TakeStruct
	public T remove(int index) {
		if (StructEnv.SAFETY_FIRST)
			if (index < 0 || index >= size)
				throw new ArrayIndexOutOfBoundsException(index);
		T got = arr[index];
		System.arraycopy(arr, index + 1, arr, index, --size - index);
		return got;
	}

	@TakeStruct
	public T removeMoveLast(int index) {
		if (StructEnv.SAFETY_FIRST)
			if (index < 0 || index >= size)
				throw new ArrayIndexOutOfBoundsException(index);
		T got = arr[index];
		arr[index] = arr[--size];
		return got;
	}

	public int size() {
		return size;
	}

	public void expandTo(int minSize) {
		arr = Struct.realloc((Class<T>) Object.class, arr, Math.max(minSize, cap * 2));
		cap = arr.length;
	}
}