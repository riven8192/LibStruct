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

		boolean[] usage = new boolean[sizeof];

		for(String field : field2offset.keySet()) {
			int offset = field2offset.get(field);
			String type = field2type.get(field);
			int sizeof = sizeof(type);

			int alignment = alignment(type);
			if(offset % alignment != 0)
				throw new IllegalStateException("struct field must be aligned to " + alignment + " bytes: " + fqcn + "." + field);

			if(offset < 0 || offset + sizeof > this.sizeof)
				throw new IllegalStateException("struct field exceeds struct bounds: " + fqcn + "." + field);

			for(int i = 0; i < sizeof; i++) {
				if(usage[offset + i])
					throw new IllegalStateException("struct field overlaps other field: " + fqcn + "." + field);
				usage[offset + i] = true;
			}
		}
	}

	private static int alignment(String type) {
		if(type.equals("Z") || type.equals("B"))
			return 1;
		if(type.equals("S") || type.equals("C"))
			return 2;
		if(type.equals("I") || type.equals("F"))
			return 4;
		if(type.equals("J") || type.equals("D"))
			return 8;
		if(type.equals("[Z") || type.equals("[B"))
			return 4;
		if(type.equals("[S") || type.equals("[C"))
			return 4;
		if(type.equals("[I") || type.equals("[F"))
			return 4;
		if(type.equals("[J") || type.equals("[D"))
			return 8;
		if(type.startsWith("["))
			throw new IllegalStateException();
		if(type.length() == 2)
			throw new IllegalStateException();
		return 4;
	}

	private static int sizeof(String type) {
		if(type.equals("Z") || type.equals("B"))
			return 1;
		if(type.equals("S") || type.equals("C"))
			return 2;
		if(type.equals("I") || type.equals("F"))
			return 4;
		if(type.equals("J") || type.equals("D"))
			return 8;
		if(type.startsWith("["))
			return sizeof(type.substring(1)); // unknown, but at least 1 element, or it wouldn't make sense to define the array
		if(type.length() == 2)
			throw new IllegalStateException();
		return 4;
	}
}
