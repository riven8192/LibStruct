package test.net.indiespot.demo.softlylit.structs.support;

import test.net.indiespot.demo.softlylit.structs.Line;
import net.indiespot.struct.cp.Struct;
import net.indiespot.struct.cp.TakeStruct;

public class LineList {

	private Line[] arr;
	private int size, cap;

	public LineList() {
		this(10);
	}

	public LineList(int cap) {
		this.cap = cap;
		arr = Struct.emptyArray(Line.class, cap);
		size = 0;
	}

	public void clear() {
		size = 0; // no need to clear out slots
	}

	public void add(Line elem) {
		if(size == cap)
			this.expand(-1);
		arr[size++] = elem;
	}

	public void addAll(LineList list) {
		for(int i = 0, len = list.size; i < len; i++) {
			this.add(list.arr[i]);
		}
	}

	@TakeStruct
	public Line get(int index) {
		if(index < 0 || index >= size)
			throw new ArrayIndexOutOfBoundsException(index);
		return arr[index];
	}

	public int size() {
		return size;
	}

	public void expand(int minSize) {
		Line[] arr2 = Struct.emptyArray(Line.class, Math.max(minSize, cap * 2));
		for(int i = 0; i < size; i++)
			arr2[i] = arr[i];
		arr = arr2;
		cap = arr.length;
	}
}
