package net.indiespot.struct.transform;

import java.util.HashMap;
import java.util.Map;

public class StructInfo {
	public int sizeof;
	public Map<String, Integer> field2offset = new HashMap<>();
	public Map<String, String> field2type = new HashMap<>();
}
