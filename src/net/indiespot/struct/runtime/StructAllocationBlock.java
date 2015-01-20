package net.indiespot.struct.runtime;

import net.indiespot.struct.transform.StructEnv;

public class StructAllocationBlock {
	final int wordSizeof;
	final int handleOffset;
	protected int wordsAllocated;

	StructAllocationBlock(int handleOffset, int sizeof) {
		this.handleOffset = handleOffset;
		this.wordSizeof = bytesToWords(sizeof);
		this.wordsAllocated = 0;
	}

	public void reset() {
		wordsAllocated = 0;
	}

	public int allocate(int sizeof) {
		if(StructEnv.SAFETY_FIRST)
			if(sizeof <= 0)
				throw new IllegalArgumentException();

		if(StructEnv.SAFETY_FIRST)
			if(!this.canAllocate(sizeof))
				throw new StructAllocationBlockOverflowError();

		int handleIndex = wordsAllocated;
		wordsAllocated += bytesToWords(sizeof);
		return handleOffset + handleIndex;
	}

	public boolean canAllocate(int sizeof) {
		return (sizeof > 0) && (wordsAllocated + bytesToWords(sizeof) <= this.wordSizeof);
	}

	public boolean isOnBlock(int handle) {
		int rel = handle - handleOffset;
		return rel >= 0 && rel < wordSizeof;
	}

	private static int bytesToWords(int sizeof) {
		if(StructEnv.SAFETY_FIRST)
			if((sizeof & 3) != 0)
				throw new RuntimeException();
		return sizeof >> 2;
	}
}
