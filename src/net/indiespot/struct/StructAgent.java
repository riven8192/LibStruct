package net.indiespot.struct;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.Map;

import net.indiespot.struct.transform.StructEnv;

public class StructAgent {

	public static void premain(String args, Instrumentation inst) {
		System.out.println("StructAgent: reading struct-defs from resource: '" + args + "'");

		Map<String, StructInfo> fqcn2info;
		try (InputStream in = StructAgent.class.getClassLoader().getResourceAsStream(args)) {
			fqcn2info = processStructDefinitionInfo(new BufferedReader(new InputStreamReader(in, "ASCII")));
		}
		catch (Throwable cause) {
			cause.printStackTrace();
			return;
		}
		for(StructInfo structInfo : fqcn2info.values())
			StructEnv.addStruct(structInfo);

		System.out.println("StructAgent: initiating application...");

		inst.addTransformer(new ClassFileTransformer() {
			@Override
			public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
				// don't rewrite classes we know are bound to be none of our business
				if(className.startsWith("java/"))
					return null;
				if(className.startsWith("javax/"))
					return null;
				if(className.startsWith("sun/"))
					return null;

				try {
					return StructEnv.rewriteClass(className, classfileBuffer);
				}
				catch (Throwable t) {
					t.printStackTrace();
					System.exit(-1);
					return null;
				}
			}
		});
	}

	private static Map<String, StructInfo> processStructDefinitionInfo(BufferedReader br) throws IOException {
		Map<String, StructInfo> fqcn2info = new HashMap<>();

		while (true) {
			String line = br.readLine();
			if(line == null)
				break;
			line = line.trim();
			if(line.isEmpty())
				continue;

			String[] parts = line.split("\\s+");
			if(parts.length < 2)
				throw new IllegalStateException();

			String fqcn = parts[0];
			String prop = parts[1];

			StructInfo info = fqcn2info.get(fqcn);
			if(info == null) {
				fqcn2info.put(fqcn, info = new StructInfo(fqcn));
				System.out.println("StructAgent: found struct: " + fqcn);
			}

			if(prop.equals("SIZEOF")) {
				info.setSizeof(Integer.parseInt(parts[2]));
			}
			else if(prop.equals("FIELD")) {
				info.addField(parts[2], parts[3], Integer.parseInt(parts[4]));
			}
			else if(prop.equals("SKIPZEROFILL")) {
				info.skipZeroFill();
			}
			else {
				throw new IllegalStateException("unexpected property: " + prop);
			}
		}

		return fqcn2info;
	}
}
