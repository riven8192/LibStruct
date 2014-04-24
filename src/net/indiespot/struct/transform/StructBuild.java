package net.indiespot.struct.transform;

import static org.objectweb.asm.Opcodes.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import net.indiespot.struct.cp.CopyStruct;
import net.indiespot.struct.cp.TakeStruct;
import net.indiespot.struct.cp.StructType;
import net.indiespot.struct.cp.StructField;
import net.indiespot.struct.runtime.StructMemory;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.util.TraceClassVisitor;

import test.net.indiespot.struct.StructUtil;

public class StructBuild {
	public static void main(String[] args) throws Exception {
		File bin = new File("./bin/");
		System.out.println(bin.getAbsolutePath());

		gatherClassesInDirectory(bin);

		File jar = new File("./lib/output.jar");
		jar.delete();
		Map<String, byte[]> fqcn2bytecode = rewriteClasses();
		try (FileOutputStream out = new FileOutputStream(jar)) {
			jar(fqcn2bytecode, out);
		}

		String[] cmds = new String[4];
		cmds[0] = "C:\\Program Files\\Java\\jdk1.7.0_45\\bin\\java.exe";
		cmds[1] = "-cp";
		cmds[2] = "./lib/asm-4.2/asm-all-4.2.jar;./lib/output.jar;./bin";
		cmds[3] = "test.net.indiespot.struct.StructTest";

		Process proc = Runtime.getRuntime().exec(cmds);
		final InputStream stdout = proc.getInputStream();
		final InputStream stderr = proc.getErrorStream();

		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					copy(stdout, System.out);
				}
				catch (IOException exc) {
					exc.printStackTrace();
				}
			}
		}).start();

		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					copy(stderr, System.err);
				}
				catch (IOException exc) {
					exc.printStackTrace();
				}
			}
		}).start();

		System.out.println("process closed with: " + proc.waitFor());
		jar.delete();
		jar.deleteOnExit();
	}

	public static void copy(InputStream src, OutputStream dst) throws IOException {
		byte[] tmp = new byte[4 * 1024];
		for(int got; (got = src.read(tmp)) != -1;) {
			dst.write(tmp, 0, got);
		}
	}

	public static void gatherClassesInDirectory(File dir) throws IOException {
		for(File file : dir.listFiles()) {
			if(file.isDirectory()) {
				gatherClassesInDirectory(file);
				continue;
			}

			if(!file.getName().endsWith(".class")) {
				continue;
			}

			try (FileInputStream fis = new FileInputStream(file)) {
				ByteArrayOutputStream baos = new ByteArrayOutputStream();

				byte[] tmp = new byte[4 * 1024];
				for(int got; (got = fis.read(tmp)) != -1;) {
					baos.write(tmp, 0, got);
				}

				registerClass(baos.toByteArray());
			}
		}
	}

	public static void jar(Map<String, byte[]> fqcn2bytecode, OutputStream dst) throws IOException {
		try (JarOutputStream jos = new JarOutputStream(dst)) {
			for(Entry<String, byte[]> entry : fqcn2bytecode.entrySet()) {
				JarEntry jarEntry = new JarEntry(entry.getKey() + ".class");

				jos.putNextEntry(jarEntry);
				jos.write(entry.getValue());
				jos.closeEntry();
			}
		}
	}

	private static enum ReturnValueStrategy {
		COPY, PASS
	}

	private static Map<String, byte[]> fqcn2bytecode = new HashMap<>();
	private static Map<String, StructInfo> struct2info = new HashMap<>();
	private static Map<String, Map<String, ReturnValueStrategy>> fqcn2method2strategy = new HashMap<>();

	public static void registerClass(byte[] bytecode) {
		gatherClassInfo(bytecode);
	}

	public static Map<String, byte[]> rewriteClasses() {

		final String printClass = "test/net/indiespot/struct/StructTest$TestStack";

		Map<String, byte[]> output = new HashMap<>();
		for(Entry<String, byte[]> entry : fqcn2bytecode.entrySet()) {
			String className = entry.getKey();
			byte[] bytecode1 = entry.getValue();

			if(className.startsWith("net/indiespot/struct/"))
				continue;

			System.out.println("rewriting class: " + className);

			if(className.equals(printClass))
				printClass(bytecode1);

			byte[] bytecode2 = rewriteClass(className, bytecode1);

			if(className.equals(printClass))
				printClass(bytecode2);

			output.put(className, bytecode2);
		}
		return output;
	}

	private static void gatherClassInfo(final byte[] bytecode) {
		ClassWriter writer = new ClassWriter(0);
		ClassVisitor visitor = new ClassVisitor(Opcodes.ASM4, writer) {
			private String fqcn;
			private StructInfo info;

			@Override
			public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
				fqcn = name;
				System.out.println("found class: " + fqcn);
				fqcn2bytecode.put(fqcn, bytecode);
			}

			// find struct.sizeof
			@Override
			public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
				if(desc.equals("L" + jvmClassName(StructType.class) + ";")) {
					System.out.println("\tfound struct: " + fqcn);
					struct2info.put(fqcn, info = new StructInfo());
					return new AnnotationVisitor(Opcodes.ASM4, super.visitAnnotation(desc, visible)) {
						public void visit(String name, Object value) {
							if(name.equals("sizeof")) {
								info.sizeof = ((Integer) value).intValue();
							}
						}
					};
				}
				return super.visitAnnotation(desc, visible);
			}

			// find fields.[name,offset]
			@Override
			public FieldVisitor visitField(int access, final String fieldName, String desc, String signature, Object value) {
				if(struct2info.containsKey(fqcn) && (access & Opcodes.ACC_STATIC) == 0) {
					info.field2type.put(fieldName, desc);
					System.out.println("\t\tfound field name: " + fieldName);

					return new FieldVisitor(Opcodes.ASM4, super.visitField(access, fieldName, desc, signature, value)) {
						public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
							if(desc.equals("L" + jvmClassName(StructField.class) + ";")) {
								return new AnnotationVisitor(Opcodes.ASM4, super.visitAnnotation(desc, visible)) {
									public void visit(String name, Object value) {
										if(name.equals("offset")) {
											info.field2offset.put(fieldName, (Integer) value);
											System.out.println("\t\tfound field offset: " + value);
										}
										super.visit(name, value);
									};
								};
							}
							return super.visitAnnotation(desc, visible);
						}
					};
				}

				return super.visitField(access, fieldName, desc, signature, value);
			}

			// find method.@[Copy|Pass]ReturnValue
			@Override
			public MethodVisitor visitMethod(int access, final String methodName, final String methodDesc, String signature, String[] exceptions) {
				return new MethodVisitor(Opcodes.ASM4, super.visitMethod(access, methodName, methodDesc, signature, exceptions)) {
					@Override
					public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
						if(desc.equals("L" + jvmClassName(CopyStruct.class) + ";")) {
							Map<String, ReturnValueStrategy> method2strategy = fqcn2method2strategy.get(fqcn);
							if(method2strategy == null)
								fqcn2method2strategy.put(fqcn, method2strategy = new HashMap<>());
							method2strategy.put(methodName + methodDesc, ReturnValueStrategy.COPY);
							System.out.println("\t\tfound method: " + methodName + methodDesc);
							System.out.println("\t\t\twith struct return value, with Copy strategy");
						}
						else if(desc.equals("L" + jvmClassName(TakeStruct.class) + ";")) {
							Map<String, ReturnValueStrategy> method2strategy = fqcn2method2strategy.get(fqcn);
							if(method2strategy == null)
								fqcn2method2strategy.put(fqcn, method2strategy = new HashMap<>());
							method2strategy.put(methodName + methodDesc, ReturnValueStrategy.PASS);
							System.out.println("\t\tfound method: " + methodName + methodDesc);
							System.out.println("\t\t\twith struct return value, with Pass strategy");
						}
						return super.visitAnnotation(desc, visible);
					}
				};
			}
		};
		new ClassReader(bytecode).accept(visitor, 0);
	}

	private static byte[] rewriteClass(final String fqcn, byte[] bytecode) {
		final ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);

		ClassVisitor visitor = new ClassVisitor(Opcodes.ASM4, writer) {

			@Override
			public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
				super.visit(version, access, name, signature, superName, interfaces);
			}

			@Override
			public void visitSource(String source, String debug) {
				super.visitSource(source, debug);
			}

			@Override
			public void visitOuterClass(String owner, String name, String desc) {
				super.visitOuterClass(owner, name, desc);
			}

			@Override
			public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
				return super.visitAnnotation(desc, visible);
			}

			@Override
			public void visitAttribute(Attribute attr) {
				super.visitAttribute(attr);
			}

			@Override
			public void visitInnerClass(String name, String outerName, String innerName, int access) {
				super.visitInnerClass(name, outerName, innerName, access);
			}

			@Override
			public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
				System.out.println("\tfield: " + name);

				if(struct2info.containsKey(fqcn)) {
					if((access & ACC_STATIC) == 0) {
						System.out.println("\tremoved field");
						return null; // remove instance fields
					}
				}

				return super.visitField(access, name, desc, signature, value);
			}

			@Override
			public MethodVisitor visitMethod(int access, final String methodName, String methodDesc, String signature, String[] exceptions) {
				final String origMethodDesc = methodDesc;
				System.out.println("\tmethod: " + methodName + " " + methodDesc);

				String returnsStructType = null;
				for(String struct : struct2info.keySet()) {
					if(methodDesc.endsWith(")L" + struct + ";"))
						returnsStructType = struct;
					methodDesc = methodDesc.replace("L" + struct + ";", "I");
				}
				final String _returnsStructType = returnsStructType;

				if(struct2info.containsKey(fqcn)) {
					if(methodName.equals("<init>"))
						return null;

					if((access & ACC_STATIC) == 0) {
						// make instance methods static
						// add 'this' as first parameter
						access |= ACC_STATIC;
						methodDesc = "(I" + methodDesc.substring(1);
					}
				}

				System.out.println("\tmethod: " + methodName + " " + methodDesc);

				final MethodVisitor mv = super.visitMethod(access, methodName, methodDesc, signature, exceptions);
				final FlowAnalysisMethodVisitor flow = new FlowAnalysisMethodVisitor(mv, access, methodName, methodDesc, signature, exceptions);
				return new MethodVisitor(Opcodes.ASM4, flow) {

					@Override
					public void visitCode() {
						if(_returnsStructType != null) {
							String msg = "";
							msg += "must define how struct return values are handled: ";
							msg += "\n\t\t" + fqcn + "." + methodName + origMethodDesc;

							Map<String, ReturnValueStrategy> method2strategy = fqcn2method2strategy.get(fqcn);
							if(method2strategy == null)
								throw new IllegalStateException(msg);
							if(!method2strategy.containsKey(methodName + origMethodDesc))
								throw new IllegalStateException(msg);
						}

						super.visitCode();

						super.visitMethodInsn(INVOKESTATIC, jvmClassName(StructMemory.class), "saveStack", "()V");
					}

					@Override
					public void visitFrame(int type, int nLocal, Object[] local, int nStack, Object[] stack) {
						if(local != null) {
							for(int i = 0; i < local.length; i++) {
								if(struct2info.containsKey(local[i]))
									local[i] = "I";
							}
						}
						if(stack != null) {
							for(int i = 0; i < stack.length; i++) {
								if(struct2info.containsKey(stack[i]))
									stack[i] = "I";
							}
						}

						super.visitFrame(type, nLocal, local, nStack, stack);
					}

					@Override
					public void visitTypeInsn(int opcode, String type) {

						if(opcode == NEW) {
							if(struct2info.containsKey(type)) {
								super.visitIntInsn(Opcodes.BIPUSH, struct2info.get(type).sizeof);
								super.visitMethodInsn(Opcodes.INVOKESTATIC, StructBuild.jvmClassName(StructMemory.class), "allocate", "(I)I");
								return;
							}
						}
						else if(opcode == INSTANCEOF) {
							if(flow.stack.peek() == VarType.INT) {
								super.visitInsn(POP); // ref
								super.visitInsn(ICONST_0); // false
								return;
							}
						}
						else if(opcode == CHECKCAST) {
							if(flow.stack.peek() == VarType.INT) {
								//super.visitInsn(POP); // ref
								//super.visitMethodInsn(Opcodes.INVOKESTATIC, jvmClassName(StructMemory.class), "execCheckcastInsn", "()V");
								return;
							}
						}

						super.visitTypeInsn(opcode, type);
					}

					@Override
					public void visitFieldInsn(int opcode, String owner, String name, String desc) {
						if(opcode == PUTFIELD) {
							if(struct2info.containsKey(owner)) {
								StructInfo info = struct2info.get(owner);
								int offset = info.field2offset.get(name).intValue();
								String type = info.field2type.get(name);
								super.visitIntInsn(BIPUSH, offset);
								super.visitMethodInsn(INVOKESTATIC, StructBuild.jvmClassName(StructMemory.class), "write", "(I" + type + "I)V");
								return;
							}
						}
						else if(opcode == GETFIELD) {
							if(struct2info.containsKey(owner)) {
								StructInfo info = struct2info.get(owner);
								int offset = info.field2offset.get(name).intValue();
								String type = info.field2type.get(name);
								super.visitIntInsn(BIPUSH, offset);
								super.visitMethodInsn(INVOKESTATIC, StructBuild.jvmClassName(StructMemory.class), "read", "(II)" + type);
								return;
							}
						}

						super.visitFieldInsn(opcode, owner, name, desc);
					}

					@Override
					public void visitMethodInsn(int opcode, String owner, String name, String desc) {
						final String origDesc = desc;

						String returnsStructType = null;
						for(String structType : struct2info.keySet()) {
							if(desc.endsWith(")L" + structType + ";"))
								returnsStructType = structType;
							desc = desc.replace("L" + structType + ";", "I");
						}

						if(struct2info.containsKey(owner)) {
							if(opcode == INVOKESPECIAL) {
								if(name.equals("<init>")) {
									super.visitInsn(POP); // TODO
									return;
								}
							}
							else if(opcode == INVOKEVIRTUAL) {
								// add 'this' as first parameter
								opcode = INVOKESTATIC;
								desc = "(I" + desc.substring(1);
							}
						}

						if(owner.equals(StructBuild.jvmClassName(StructUtil.class))) {
							if(name.equals("getPointer") && desc.equals("(Ljava/lang/Object;)J")) {
								if(flow.stack.peek() == VarType.INT) {
									owner = StructBuild.jvmClassName(StructMemory.class);
									name = "handle2pointer";
									desc = "(I)J";
								}
							}
							else if(name.equals("isReachable") && desc.equals("(Ljava/lang/Object;)Z")) {
								if(flow.stack.peek() == VarType.INT) {
									owner = StructBuild.jvmClassName(StructMemory.class);
									name = "isValid";
									desc = "(I)Z";
								}
							}
						}

						super.visitMethodInsn(opcode, owner, name, desc);

						if(returnsStructType != null) {
							Map<String, ReturnValueStrategy> method2strategy = fqcn2method2strategy.get(owner);
							if(method2strategy == null)
								throw new IllegalStateException();

							ReturnValueStrategy strategy = method2strategy.get(name + origDesc);
							System.out.println("\t\t\treturn value strategy: " + strategy);

							if(strategy == ReturnValueStrategy.PASS) {
								// no-op
							}
							else if(strategy == ReturnValueStrategy.COPY) {
								super.visitIntInsn(BIPUSH, struct2info.get(returnsStructType).sizeof);
								super.visitMethodInsn(INVOKESTATIC, StructBuild.jvmClassName(StructMemory.class), "allocateCopy", "(II)I");
							}
							else {
								throw new IllegalStateException();
							}
						}
					}

					@Override
					public void visitInsn(int opcode) {
						if(opcode == ACONST_NULL) {
							if(!flow.stack.isEmpty() && flow.stack.peek() == VarType.INT) {
								opcode = ICONST_0;
							}
						}
						else if(opcode == ARETURN) {
							if(!flow.stack.isEmpty() && flow.stack.peek() == VarType.INT) {
								opcode = IRETURN;
							}
						}

						switch (opcode) {
						case RETURN:
						case ARETURN:
						case IRETURN:
						case FRETURN:
						case LRETURN:
						case DRETURN:
							super.visitMethodInsn(INVOKESTATIC, jvmClassName(StructMemory.class), "restoreStack", "()V");
							break;
						}

						super.visitInsn(opcode);
					}

					@Override
					public void visitVarInsn(int opcode, int var) {
						if(opcode == ASTORE) {
							if(!flow.stack.isEmpty() && flow.stack.peek() == VarType.INT) {
								opcode = ISTORE;
							}
						}
						else if(opcode == ALOAD) {
							if(flow.local.get(var) == VarType.INT) {
								opcode = ILOAD;
							}
						}

						super.visitVarInsn(opcode, var);
					}

					@Override
					public void visitJumpInsn(int opcode, Label label) {
						if(opcode == IFNULL) {
							if(!flow.stack.isEmpty() && flow.stack.peek() == VarType.INT) {
								opcode = IFEQ;
							}
						}
						else if(opcode == IFNONNULL) {
							if(!flow.stack.isEmpty() && flow.stack.peek() == VarType.INT) {
								opcode = IFNE;
							}
						}
						else if(opcode == IF_ACMPNE) {
							if(!flow.stack.isEmpty() && flow.stack.peek() == VarType.INT) {
								if(flow.stack.peek(1) == VarType.INT) {
									opcode = IF_ICMPNE;
								}
							}
						}
						else if(opcode == IF_ACMPEQ) {
							if(!flow.stack.isEmpty() && flow.stack.peek() == VarType.INT) {
								if(flow.stack.peek(1) == VarType.INT) {
									opcode = IF_ICMPEQ;
								}
							}
						}

						super.visitJumpInsn(opcode, label);
					}
				};
			}

			@Override
			public void visitEnd() {
				super.visitEnd();
			}
		};

		new ClassReader(bytecode).accept(visitor, 0);

		return writer.toByteArray();
	}

	public static String jvmClassName(Class<?> type) {
		return type.getName().replace('.', '/');
	}

	public static void printClass(byte[] bytecode) {
		PrintWriter printWriter = new PrintWriter(System.out);
		TraceClassVisitor traceClassVisitor = new TraceClassVisitor(printWriter);
		new ClassReader(bytecode).accept(traceClassVisitor, 0);
	}
}