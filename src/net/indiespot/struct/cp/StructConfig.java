package net.indiespot.struct.cp;

import net.indiespot.struct.StructAgent;

public class StructConfig {
	static {
		String projectPackage = StructAgent.class.getPackage().getName();
		for(Object key : System.getProperties().keySet()) {
			String prop = (String) key;
			if(!prop.startsWith(projectPackage))
				continue;

			// aight?
		}
	}

	private static final String magnitude_lookup_table = "KMGT"; // kilo, mega, giga, tera

	public static final long parseVmArg(Class<?> type, String prop, long orElse, boolean asPOT) {
		prop = type.getName() + "." + prop;
		String val = System.getProperty(prop);
		if(val == null || (val = val.trim()).isEmpty())
			return orElse;

		if(val.equalsIgnoreCase("false"))
			return 0L;
		if(val.equalsIgnoreCase("true"))
			return 1L;

		long magnitude = 1L;
		int magnitudeIndex = magnitude_lookup_table.indexOf(val.charAt(val.length() - 1));
		if(magnitudeIndex != -1) {
			for(int i = 0; i <= magnitudeIndex; i++)
				magnitude *= (asPOT ? 1024 : 1000);
			val = val.substring(0, val.length() - 1);
		}

		try {
			return Long.parseLong(val) * magnitude;
		}
		catch (Exception exc) {
			throw new IllegalArgumentException("failed to parse property '" + prop + "' with value '" + System.getProperty(prop) + "'");
		}
	}
}
