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

	public int level() {
		return level;
	}

	public boolean isOnStack(long handle) {
		// whether handle is in parent stack
		return (handle < next);
	}
}
