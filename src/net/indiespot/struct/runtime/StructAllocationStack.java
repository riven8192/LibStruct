package net.indiespot.struct.runtime;

public class StructAllocationStack extends StructAllocationBlock {
	private final long[] stack = new long[100];
	private int level;

	public StructAllocationStack(long base, int sizeof) {
		super(base, sizeof);
	}

	public void save() {
		stack[level++] = next;
	}

	public int restore() {
		long was = next;
		next = stack[--level];
		return (int) (was - next);
	}

	public void flush() { // restore & save
		next = stack[level - 1];
	}

	public int level() {
		return level;
	}

	public boolean isValid(long pntr) {
		return this.isOnBlock(pntr) && (pntr < next);
	}

	public boolean isInvalid(long pntr) {
		return this.isOnBlock(pntr) && (pntr >= next);
	}
}
