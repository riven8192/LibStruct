package test.net.indiespot.demo.softlylit.structs.support;

import test.net.indiespot.demo.softlylit.structs.Line;

import net.indiespot.struct.cp.Struct;
import net.indiespot.struct.cp.TakeStruct;
import net.indiespot.struct.transform.StructEnv;

public class LineBlock {
	private final int length;
	private final Line base;

	private int size;

	public LineBlock(int length) {
		this.length = length;
		this.base = Struct.malloc(Line.class, length)[0];
		this.size = 0;
	}

	public void clear() {
		size = 0;
	}

	public int size() {
		return size;
	}

	@TakeStruct
	public Line add() {
		return this.get(size++);
	}

	@TakeStruct
	public Line add(Line src) {
		Line dst = this.add();
		Struct.copy(Line.class, src, dst);
		return dst;
	}

	public void addAll(LineBlock src) {
		if (StructEnv.SAFETY_FIRST)
			if (src.size > this.length - this.size)
				throw new IllegalStateException();
		Struct.copy(Line.class, src.base, this.get(this.size), src.size);
		this.size += src.size;
	}

	@TakeStruct
	public Line get(int index) {
		if (StructEnv.SAFETY_FIRST)
			if (index < 0 || index >= size)
				throw new IllegalStateException();
		return Struct.sibling(base, Line.class, index);
	}

	public void set(int index, Line value) {
		Struct.copy(Line.class, value, this.get(index));
	}

	public void free() {
		Line[] arr = Struct.emptyArray(Line.class, length);
		for (int i = 0; i < length; i++)
			arr[i] = this.get(i);
		Struct.free(arr);
	}
}