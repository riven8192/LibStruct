package net.indiespot.struct.runtime;

public class StructAllocationBlock {
	final int wordSizeof;
	final int handleOffset;
	int wordsAllocated;

	public StructAllocationBlock(int handleOffset, int sizeof) {
		this.handleOffset = handleOffset;
		this.wordSizeof = bytesToWords(sizeof);
		this.wordsAllocated = 0;
	}

	public int allocate(int sizeof) {
		if(sizeof <= 0)
			throw new IllegalArgumentException();

		int wordSizeof = bytesToWords(sizeof);
		if(wordsAllocated + wordSizeof > this.wordSizeof)
			throw new OutOfMemoryError();

		int handleIndex = wordsAllocated;
		wordsAllocated += wordSizeof;
		return handleOffset + handleIndex;
	}

	public boolean isOnBlock(int handle) {
		int rel = handle - handleOffset;
		return rel >= 0 && rel < wordSizeof;
	}

	private static int bytesToWords(int sizeof) {
		if((sizeof & 3) != 0)
			throw new RuntimeException();
		return sizeof >> 2;
	}
}
