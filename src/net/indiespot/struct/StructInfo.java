package net.indiespot.struct;

import java.util.HashMap;
import java.util.Map;

public class StructInfo {
	public final String fqcn;
	public int sizeof = -1;
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

		System.out.println("StructInfo[" + fqcn + "] sizeof=" + sizeof);
	}

	public void skipZeroFill() {
		skipZeroFill = true;

		System.out.println("StructInfo[" + fqcn + "] skipZeroFill");
	}

	public void addField(String name, String type, int byteOffset) {
		if(byteOffset < 0)
			throw new IllegalArgumentException("field byte offset must not be negative");
		field2type.put(name, type);
		field2offset.put(name, byteOffset);

		System.out.println("StructInfo[" + fqcn + "] field=" + name + " " + type + " @" + byteOffset);
	}

	public void validate() {
		if(sizeof == -1)
			throw new IllegalStateException("unspecified [" + fqcn + "].sizeof");
		if(field2type.size() != field2offset.size())
			throw new IllegalStateException("unspecified [" + fqcn + "].fields");
	}
}
