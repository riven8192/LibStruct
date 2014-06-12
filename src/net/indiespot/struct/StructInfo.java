package net.indiespot.struct;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

public class StructInfo {
	private static final Map<String, StructInfo> fqcn2info = new HashMap<>();

	public static StructInfo lookup(String fqcn) {
		return fqcn2info.get(fqcn);
	}

	public static Collection<StructInfo> values() {
		return fqcn2info.values();
	}

	// ---

	public final String fqcn;
	public int sizeof = -1;
	public boolean skipZeroFill;

	public Map<String, Integer> field2offset = new HashMap<>();
	public Map<String, Integer> field2count = new HashMap<>();
	public Map<String, String> field2type = new HashMap<>();
	public Map<String, Boolean> field2embed = new HashMap<>();

	public StructInfo(String fqcn) {
		this.fqcn = fqcn;

		if(fqcn2info.put(fqcn, this) != null)
			throw new IllegalStateException();
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

	public void addField(String name, String type, int byteOffset, int elemCount, boolean embed) {
		if(byteOffset < 0)
			throw new IllegalArgumentException("field byte offset must not be negative");
		if(elemCount <= 0)
			throw new IllegalArgumentException("field element count must be positive");
		field2type.put(name, type);
		field2offset.put(name, byteOffset);
		field2count.put(name, elemCount);
		field2embed.put(name, embed);

		System.out.println("StructInfo[" + fqcn + "] field=" + name + " " + type + " @" + byteOffset);
	}

	public void setFieldCount(String name, int elemCount) {
		if(elemCount <= 0)
			throw new IllegalArgumentException("field element count must be positive");
		if(!field2count.containsKey(name))
			throw new IllegalStateException();
		field2count.put(name, elemCount);
	}

	public void setFieldEmbed(String name, boolean embed) {
		if(!field2embed.containsKey(name))
			throw new IllegalStateException();
		field2embed.put(name, embed);
	}

	public void validate() {
		if(sizeof == -1)
			throw new IllegalStateException("unspecified [" + fqcn + "].sizeof");
		if(field2type.size() != field2offset.size())
			throw new IllegalStateException("unspecified [" + fqcn + "].fields");

		boolean[] usage = new boolean[sizeof];

		for(String field : field2offset.keySet()) {
			int offset = field2offset.get(field).intValue();
			boolean embed = field2embed.get(field).booleanValue();
			String type = field2type.get(field);
			int range = field2count.get(field).intValue() * sizeof(type, embed);

			int alignment = alignment(type);
			if(offset % alignment != 0)
				throw new IllegalStateException("struct field must be aligned to " + alignment + " bytes: " + fqcn + "." + field);

			if(offset < 0 || offset + range > sizeof)
				throw new IllegalStateException("struct field exceeds struct bounds: " + fqcn + "." + field);

			for(int i = 0; i < range; i++) {
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
		return 4; // struct type
	}

	private static int sizeof(String type, boolean embed) {
		if(type.equals("Z") || type.equals("B"))
			return 1;
		if(type.equals("S") || type.equals("C"))
			return 2;
		if(type.equals("I") || type.equals("F"))
			return 4;
		if(type.equals("J") || type.equals("D"))
			return 8;
		if(type.startsWith("["))
			return sizeof(type.substring(1), embed); // unknown, but at least 1 element, or it wouldn't make sense to define the array
		if(type.length() == 2)
			throw new IllegalStateException();
		StructInfo info = lookup(type.substring(1, type.length()-1));
		if(info == null)
			throw new NoSuchElementException("struct type not found: " + type);
		return embed ? info.sizeof : 4; // struct type
	}
}
