package net.indiespot.struct;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import net.indiespot.struct.StructAgentDelegate.BytecodeLoader;
import net.indiespot.struct.transform.StructEnv;

public class StructPacker {
	public static void main(String[] args) throws Exception {
		if (args.length == 0) {
			System.out.println("Usage:");
			System.out.println(" -structdef [path]  (any number of times)");
			System.out.println(" -jar [path]        (any number of times)");
			System.out.println();
			System.out.println("Example:");
			System.out.println(" -structdef [path1] -jar [path2] -jar [path3] -jar [path4] -structdef [path5] -jar [path6]");
			return;
		}

		List<String> structdefs = new ArrayList<>();
		List<String> jars = new ArrayList<>();

		final Map<String, byte[]> fqcn2bytecode = new HashMap<>();

		for (int i = 0; i < args.length; i += 2) {
			String key = args[i + 0];
			String val = args[i + 1];

			if (key.equals("-structdef"))
				structdefs.add(val);
			else if (key.equals("-jar"))
				jars.add(val);
			else
				throw new IllegalStateException();
		}

		System.out.println("processing " + jars.size() + " jar files...");

		for (String jar : jars) {
			System.out.println("\t processing jar file: " + jar);
			try (JarInputStream jis = new JarInputStream(new FileInputStream(jar))) {
				while (true) {
					ZipEntry entry = jis.getNextEntry();
					if (entry == null)
						break;
					if (!entry.getName().endsWith(".class"))
						continue;

					String fqcn = entry.getName();
					fqcn = fqcn.substring(0, fqcn.length() - ".class".length());
					fqcn = fqcn.replace('\\', '/');
					if (fqcn.startsWith("/"))
						fqcn = fqcn.substring(1);

					if (fqcn.startsWith("net/indiespot/struct/codegen/"))
						continue;
					if (fqcn.equals("test/net/indiespot/struct/StructTest$TestCollectionAPI"))
						continue;
					if (fqcn.equals("test/net/indiespot/struct/StructTest$TestDuplicateOverloadedMethod"))
						continue;

					byte[] bytecode = readFully(jis);
					fqcn2bytecode.put(fqcn, bytecode);
				}
			}
		}

		if (!fqcn2bytecode.containsKey("net/indiespot/struct/cp/Struct"))
			throw new IllegalStateException("LibStruct-jar required in jar-file list to process");

		BytecodeLoader loader = new BytecodeLoader() {
			@Override
			public byte[] load(String fqcn) {
				if (fqcn.contains("."))
					throw new IllegalStateException();
				return fqcn2bytecode.get(fqcn);
			}
		};

		{
			System.out.println("\t processing " + structdefs.size() + " structdef files");
			for (String structdef : structdefs) {
				System.out.println("\t processing structdef file: " + structdef);
				try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(structdef), "ASCII"))) {
					StructAgentDelegate.processStructDefinitionInfo(reader, loader);
				}
			}

			for (StructInfo structInfo : StructInfo.values())
				StructEnv.addStruct(structInfo);
			StructEnv.linkStructs();
		}

		String outJarFile = "./libstruct-cp-prefix.jar";

		try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(outJarFile))) {

			for (Entry<String, byte[]> entry : fqcn2bytecode.entrySet()) {
				String fqcn = entry.getKey();
				byte[] bytecode = entry.getValue();

				bytecode = StructEnv.rewriteClass(fqcn, bytecode);
				if (bytecode == null)
					continue; // no rewrite required

				System.out.println("\t transformed " + fqcn);

				jos.putNextEntry(new ZipEntry(fqcn + ".class"));
				jos.write(bytecode);
				jos.closeEntry();
			}
		}

		System.out.println("Generated " + outJarFile);
		System.out.println("\t Full path: " + new File(outJarFile).getCanonicalPath());
		System.out.println("\t Put generated file as first entry in the classpath of your application, so that rewritten classes take precedence.");
		System.out.println("Done.");
	}

	private static byte[] readFully(InputStream is) throws IOException {
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		byte[] tmp = new byte[4096];
		while (true) {
			int got = is.read(tmp);
			if (got == -1)
				break;
			os.write(tmp, 0, got);
		}
		return os.toByteArray();
	}
}
