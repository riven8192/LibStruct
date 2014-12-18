package test.net.indiespot.demo.softlylit.structs;

import test.net.indiespot.demo.softlylit.structs.Triangle;
import net.indiespot.struct.cp.Struct;
import net.indiespot.struct.cp.TakeStruct;

public class TriangleList {

	private Triangle[] arr;
	private int size, cap;
	
	public TriangleList() {
		this(10);
	}

	public TriangleList(int cap) {
		if (cap <= 0)
			throw new IllegalArgumentException();
		this.expandTo(cap);
	}

	public void clear() {
		size = 0;
	}

	public void add(Triangle elem) {
		if (size == cap)
			this.expandTo(-1);
		arr[size++] = elem;
	}

	public void addRange(TriangleList list, int off, int len) {
		if (len < 0)
			throw new IllegalArgumentException();
		if (off + len > list.size)
			throw new IllegalArgumentException();
		for (int i = 0; i < len; i++) {
			this.add(list.arr[off + i]);
		}
	}

	public void addAll(TriangleList list) {
		for (int i = 0, len = list.size; i < len; i++) {
			this.add(list.arr[i]);
		}
	}

	@TakeStruct
	public Triangle get(int index) {
		if (index < 0 || index >= size)
			throw new ArrayIndexOutOfBoundsException(index);
		return arr[index];
	}

	@TakeStruct
	public Triangle remove(int index) {
		if (index < 0 || index >= size)
			throw new ArrayIndexOutOfBoundsException(index);
		Triangle got = arr[index];
		System.arraycopy(arr, index + 1, arr, index, --size - index);
		return got;
	}

	@TakeStruct
	public Triangle removeMoveLast(int index) {
		if (index < 0 || index >= size)
			throw new ArrayIndexOutOfBoundsException(index);
		Triangle got = arr[index];
		arr[index] = arr[--size];
		return got;
	}

	public int size() {
		return size;
	}

	public void expandTo(int minSize) {
		Triangle[] arr2 = Struct.emptyArray(Triangle.class, Math.max(minSize, cap * 2));
		for (int i = 0; i < size; i++)
			arr2[i] = arr[i];
		arr = arr2;
		cap = arr.length;
	}
	
	public TriangleList self() {
		return new TriangleList();
	}
}