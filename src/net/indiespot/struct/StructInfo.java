package net.indiespot.struct;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
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

	public List<String> fieldNames = new ArrayList<>();
	public Map<String, Integer> field2offset = new HashMap<>();
	public Map<String, Integer> field2count = new HashMap<>();
	public Map<String, String> field2type = new HashMap<>();
	public Map<String, Boolean> field2embed = new HashMap<>();

	public StructInfo(String fqcn) {
		this.fqcn = fqcn;

		if (fqcn2info.put(fqcn, this) != null)
			throw new IllegalStateException();
	}

	public void setSizeof(int sizeof) {
		if (sizeof == -1)
			return;
		if (sizeof <= 0 || sizeof % 4 != 0)
			throw new IllegalArgumentException("struct sizeof must be a multiple of 4");
		this.sizeof = sizeof;

		System.out.println("StructInfo[" + fqcn + "] sizeof=" + sizeof);
	}

	public void skipZeroFill() {
		skipZeroFill = true;

		System.out.println("StructInfo[" + fqcn + "] skipZeroFill");
	}

	public void addField(String name, String type, int byteOffset, int elemCount, boolean embed) {
		if (byteOffset < -1)
			throw new IllegalArgumentException("field byte offset must not be negative");
		if (elemCount <= 0)
			throw new IllegalArgumentException("field element count must be positive");

		fieldNames.add(name);
		field2type.put(name, type);
		field2offset.put(name, Integer.valueOf(byteOffset));
		field2count.put(name, Integer.valueOf(elemCount));
		field2embed.put(name, Boolean.valueOf(embed));
	}

	public int calcSizeof() {
		this.layout();
		if (sizeof <= 0)
			throw new IllegalStateException();
		return sizeof;
	}

	private boolean isLaidOut = false;

	private void layout() {
		if (isLaidOut)
			return;
		isLaidOut = true;

		int calcSizeof = 0;

		for (String field : fieldNames) {
			int offset = field2offset.get(field).intValue();
			boolean embed = field2embed.get(field).booleanValue();
			String type = field2type.get(field);
			int count = field2count.get(field).intValue();

			if (offset == -1) {
				offset = calcSizeof;

				int alignment = alignment(type);
				offset = align(offset, alignment);
				field2offset.put(field, Integer.valueOf(offset));
			}
			int fieldWidth = sizeof(type, embed) * count;

			System.out.println("StructInfo[" + fqcn + "] field=" + field + ", type=" + type + ", offset=" + offset + ", sizeof=" + fieldWidth + ", alignment=" + alignment(type));

			calcSizeof = Math.max(offset + fieldWidth, calcSizeof);
		}
		calcSizeof = align(calcSizeof, 4);

		if (sizeof == -1)
			sizeof = calcSizeof;
		else if (sizeof < calcSizeof)
			throw new IllegalStateException("struct sizeof " + fqcn + " was defined as " + sizeof + ", while " + calcSizeof + " is required");

		System.out.println("StructInfo[" + fqcn + "] struct sizeof=" + sizeof);
	}

	public void validate() {
		if (field2type.size() != field2offset.size())
			throw new IllegalStateException("unspecified [" + fqcn + "].fields");

		this.layout();

		String[] usage = new String[sizeof];

		for (String field : field2offset.keySet()) {
			int offset = field2offset.get(field).intValue();
			boolean embed = field2embed.get(field).booleanValue();
			String type = field2type.get(field);
			int range = field2count.get(field).intValue() * sizeof(type, embed);

			int alignment = alignment(type);
			if (offset % alignment != 0)
				throw new IllegalStateException("struct field must be aligned to " + alignment + " bytes: " + fqcn + "." + field);

			if (offset < 0 || offset + range > sizeof)
				throw new IllegalStateException("struct field exceeds struct bounds: " + fqcn + "." + field);

			for (int i = 0; i < range; i++) {
				if (usage[offset + i] != null)
					throw new IllegalStateException("struct field overlaps other field: " + fqcn + "." + field + " (." + usage[offset + i] + ")");
				usage[offset + i] = field;
			}
		}
	}

	private int align(int offset, int alignment) {
		while (offset % alignment != 0)
			offset++;
		return offset;
	}

	private static int alignment(String type) {
		if (type.equals("Z") || type.equals("B"))
			return 1;
		if (type.equals("S") || type.equals("C"))
			return 2;
		if (type.equals("I") || type.equals("F"))
			return 4;
		if (type.equals("J") || type.equals("D"))
			return 8;
		if (type.equals("[Z") || type.equals("[B"))
			return 4;
		if (type.equals("[S") || type.equals("[C"))
			return 4;
		if (type.equals("[I") || type.equals("[F"))
			return 4;
		if (type.equals("[J") || type.equals("[D"))
			return 8;
		if (type.startsWith("["))
			throw new IllegalStateException();
		if (type.length() == 2)
			throw new IllegalStateException();
		return 4; // struct type
	}

	private static int sizeof(String type, boolean embed) {
		if (type.equals("Z") || type.equals("B"))
			return 1;
		if (type.equals("S") || type.equals("C"))
			return 2;
		if (type.equals("I") || type.equals("F"))
			return 4;
		if (type.equals("J") || type.equals("D"))
			return 8;
		if (type.startsWith("["))
			return sizeof(type.substring(1), embed); // unknown, but at least 1
			                                         // element, or it wouldn't
			                                         // make sense to define the
			                                         // array
		if (type.length() == 2)
			throw new IllegalStateException();

		StructInfo info = lookup(type.substring(1, type.length() - 1));
		if (info == null)
			throw new NoSuchElementException("struct type not found: " + type);
		return embed ? info.calcSizeof() : 8; // struct type
	}
}
