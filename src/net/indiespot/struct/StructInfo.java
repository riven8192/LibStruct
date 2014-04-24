package net.indiespot.struct;

import java.util.HashMap;
import java.util.Map;

public class StructInfo {
	public final String fqcn;
	public int sizeof;
	public boolean skipZeroFill;

	public Map<String, Integer> field2offset = new HashMap<>();
	public Map<String, String> field2type = new HashMap<>();

	public StructInfo(String fqcn) {
		this.fqcn = fqcn;
	}

	public void setSizeof(int sizeof) {
		if(sizeof <= 0 || sizeof % 4 != 0)
			throw new IllegalArgumentException("struct sizeof must be a multiple of 4");
		this.sizeof = sizeof;
	}

	public void skipZeroFill() {
		skipZeroFill = true;
	}

	public void addField(String name, String type, int byteOffset) {
		if(byteOffset < 0)
			throw new IllegalArgumentException("field byte offset must not be negative");
		field2type.put(name, type);
		field2offset.put(name, byteOffset);
	}
}
