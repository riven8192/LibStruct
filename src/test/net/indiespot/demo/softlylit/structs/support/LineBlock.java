package test.net.indiespot.demo.softlylit.structs.support;

import test.net.indiespot.demo.softlylit.structs.Line;

import net.indiespot.struct.cp.Struct;
import net.indiespot.struct.cp.TakeStruct;
import net.indiespot.struct.transform.StructEnv;

public class LineBlock {
	private final int cap;
	private final Line base;

	private int size;

	public LineBlock(int cap) {
		if (cap <= 0)
			throw new IllegalArgumentException();
		this.cap = cap;
		this.base = Struct.mallocBlock(Line.class, cap);
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

	public void addRange(LineBlock src, int off, int len) {
		if (StructEnv.SAFETY_FIRST)
			if (off < 0 || len < 0 || off + len > src.size)
				throw new IllegalStateException();
		if (StructEnv.SAFETY_FIRST)
			if (len > this.cap - this.size)
				throw new IllegalStateException();
		int offset = this.size;
		this.size += len;
		Struct.copy(Line.class, src.get(off), this.get(offset), len);
	}

	public void addAll(LineBlock src) {
		if (StructEnv.SAFETY_FIRST)
			if (src.size > this.cap - this.size)
				throw new IllegalStateException();
		int offset = this.size;
		this.size += src.size;
		Struct.copy(Line.class, src.base, this.get(offset), src.size);
	}

	@TakeStruct
	public Line get(int index) {
		if (StructEnv.SAFETY_FIRST)
			if (index < 0 || index >= size)
				throw new IllegalStateException();
		return Struct.index(base, Line.class, index);
	}

	public void set(int index, Line value) {
		Struct.copy(Line.class, value, this.get(index));
	}

	public void free() {
		Struct.free(base);
	}
}