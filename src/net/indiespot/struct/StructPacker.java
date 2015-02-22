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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
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
			System.out.println(" -dir [path]        (any number of times)");
			System.out.println(" -out [path]        (once)");
			System.out.println();
			System.out.println("Example:");
			System.out.println("StructPacker -structdef [path0] -dir [path1] -jar [path2] -jar [path3] -jar [path4] -structdef [path5] -jar [path6] -out [path7]");
			return;
		}

		// process command-line arguments
		List<String> structdefFiles = new ArrayList<>();
		List<String> inJarFiles = new ArrayList<>();
		List<String> inDirPaths = new ArrayList<>();
		String outJarFile = "./libstruct-cp-prefix.jar";

		for (int i = 0; i < args.length; i += 2) {
			String key = args[i + 0];
			String val = args[i + 1];

			if (key.equals("-structdef"))
				structdefFiles.add(val);
			else if (key.equals("-jar"))
				inJarFiles.add(val);
			else if (key.equals("-dir"))
				inDirPaths.add(val);
			else if (key.equals("-out"))
				outJarFile = val;
			else
				throw new IllegalStateException();
		}

		final Set<String> ignores = new HashSet<>();
		{
			ignores.add("net/indiespot/struct/codegen/StructGenericsSourcecodeGenerator");
			ignores.add("net/indiespot/struct/codegen/templates/List");
			ignores.add("net/indiespot/struct/codegen/templates/Block");
			ignores.add("test/net/indiespot/struct/StructTest$TestCollectionAPI");
			ignores.add("test/net/indiespot/struct/StructTest$TestDuplicateOverloadedMethod");
		}

		// gather all classnames and their bytecode
		final Map<String, byte[]> fqcn2bytecode = new HashMap<>();

		System.out.println("processing " + inJarFiles.size() + " jar files...");
		for (String jar : inJarFiles) {
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

					if (ignores.contains(fqcn))
						continue;

					byte[] bytecode = readFully(jis);
					if (fqcn2bytecode.containsKey(fqcn) && !Arrays.equals(fqcn2bytecode.get(fqcn), bytecode))
						throw new IllegalStateException("duplicate fqcn [" + fqcn + "] with different bytecode");
					fqcn2bytecode.put(fqcn, bytecode);
				}
			}
		}

		System.out.println("processing " + inDirPaths.size() + " dir paths...");
		for (String dir : inDirPaths) {
			System.out.println("\t processing dir path: " + dir);

			traverse(new File(dir), "", new Callback() {
				@Override
				public void onFile(File file, String relative) {
					if (!relative.endsWith(".class"))
						return;

					String fqcn = relative;
					fqcn = fqcn.substring(0, fqcn.length() - ".class".length());
					fqcn = fqcn.replace('\\', '/');
					if (fqcn.startsWith("/"))
						fqcn = fqcn.substring(1);

					if (ignores.contains(fqcn))
						return;

					byte[] bytecode;
					try (FileInputStream fis = new FileInputStream(file)) {
						bytecode = readFully(fis);
					} catch (IOException exc) {
						throw new IllegalStateException(exc);
					}

					if (fqcn2bytecode.containsKey(fqcn) && !Arrays.equals(fqcn2bytecode.get(fqcn), bytecode))
						throw new IllegalStateException("duplicate fqcn [" + fqcn + "] with different bytecode");
					fqcn2bytecode.put(fqcn, bytecode);
				}
			});
		}

		if (!fqcn2bytecode.containsKey("net/indiespot/struct/cp/Struct"))
			throw new IllegalStateException("LibStruct-jar required in input jar-file list, as it rewrites its own classes");

		BytecodeLoader loader = new BytecodeLoader() {
			@Override
			public byte[] load(String fqcn) {
				if (fqcn.contains("."))
					throw new IllegalStateException();
				return fqcn2bytecode.get(fqcn);
			}
		};

		// gather struct info
		{
			System.out.println("\t processing " + structdefFiles.size() + " structdef files");
			for (String structdef : structdefFiles) {
				System.out.println("\t processing structdef file: " + structdef);
				try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(structdef), "ASCII"))) {
					StructAgentDelegate.processStructDefinitionInfo(reader, loader);
				}
			}

			for (StructInfo structInfo : StructInfo.values())
				StructEnv.addStruct(structInfo);
			StructEnv.linkStructs();
		}

		// generate jar with transformed classes
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

		System.out.println("Generated jar: " + outJarFile);
		System.out.println("\t Full path: " + new File(outJarFile).getCanonicalPath());
		System.out.println("\t Put generated file as first entry in the classpath of your application, so that rewritten classes take precedence.");
		System.out.println("Done.");
	}

	private static interface Callback {
		public void onFile(File file, String relative);
	}

	private static void traverse(File dir, String relative, Callback callback) {

		for (File file : dir.listFiles()) {
			if (file.isDirectory()) {
				traverse(file, relative + file.getName() + "/", callback);
			} else {
				callback.onFile(file, relative + file.getName());
			}
		}
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
