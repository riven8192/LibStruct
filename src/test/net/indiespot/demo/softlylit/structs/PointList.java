package test.net.indiespot.demo.softlylit.structs;

import test.net.indiespot.demo.softlylit.structs.Point;
import net.indiespot.struct.cp.Struct;
import net.indiespot.struct.cp.TakeStruct;

public class PointList {

	private Point[] arr;
	private int size, cap;
	
	public PointList() {
		this(10);
	}

	public PointList(int cap) {
		if (cap <= 0)
			throw new IllegalArgumentException();
		this.expandTo(cap);
	}

	public void clear() {
		size = 0;
	}

	public void add(Point elem) {
		if (size == cap)
			this.expandTo(-1);
		arr[size++] = elem;
	}

	public void addRange(PointList list, int off, int len) {
		if (len < 0)
			throw new IllegalArgumentException();
		if (off + len > list.size)
			throw new IllegalArgumentException();
		for (int i = 0; i < len; i++) {
			this.add(list.arr[off + i]);
		}
	}

	public void addAll(PointList list) {
		for (int i = 0, len = list.size; i < len; i++) {
			this.add(list.arr[i]);
		}
	}

	@TakeStruct
	public Point get(int index) {
		if (index < 0 || index >= size)
			throw new ArrayIndexOutOfBoundsException(index);
		return arr[index];
	}

	@TakeStruct
	public Point remove(int index) {
		if (index < 0 || index >= size)
			throw new ArrayIndexOutOfBoundsException(index);
		Point got = arr[index];
		System.arraycopy(arr, index + 1, arr, index, --size - index);
		return got;
	}

	@TakeStruct
	public Point removeMoveLast(int index) {
		if (index < 0 || index >= size)
			throw new ArrayIndexOutOfBoundsException(index);
		Point got = arr[index];
		arr[index] = arr[--size];
		return got;
	}

	public int size() {
		return size;
	}

	public void expandTo(int minSize) {
		Point[] arr2 = Struct.emptyArray(Point.class, Math.max(minSize, cap * 2));
		for (int i = 0; i < size; i++)
			arr2[i] = arr[i];
		arr = arr2;
		cap = arr.length;
	}
	
	public PointList self() {
		return new PointList();
	}
}