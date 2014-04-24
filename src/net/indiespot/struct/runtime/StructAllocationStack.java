package net.indiespot.struct.runtime;

public class StructAllocationStack extends StructAllocationBlock {
	private final int[] stack = new int[100];
	private int index;

	public StructAllocationStack(int handleOffset, int sizeof) {
		super(handleOffset, sizeof);
	}

	public void save() {
		stack[index++] = this.wordsAllocated;
	}

	public void restore() {
		this.wordsAllocated = stack[--index];
	}

	public boolean isOnStack(int handle) {
		// whether handle is in parent stack
		return (handle < handleOffset + wordsAllocated);
	}
}
