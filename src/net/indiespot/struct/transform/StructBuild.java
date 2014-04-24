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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
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
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.util.TraceClassVisitor;

import test.net.indiespot.struct.StructUtil;

public class StructBuild {
	public static final boolean print_log = false;
	private static final String print_class = null;//"test/net/indiespot/struct/StructTest$TestStructAsObjectParam";
	private static final boolean print_class_and_terminate = false;
	public static final String plain_struct_flag = "$truct";//"net/indiespot/struct/transform/StructFlag";
	public static final String wrapped_struct_flag = "L" + plain_struct_flag + ";";

	public static void main(String[] args) throws Exception {
		File bin = new File("./bin/");
		if(print_log)
			System.out.println(bin.getAbsolutePath());

		gatherClassesInDirectory(bin);

		File jar = new File("./lib/output.jar");
		jar.delete();
		Map<String, byte[]> fqcn2bytecode = rewriteClasses();
		try (FileOutputStream out = new FileOutputStream(jar)) {
			jar(fqcn2bytecode, out);
		}

		List<String> cmds = new ArrayList<>();
		cmds.add(System.getProperty("java.home") + "/bin/java.exe");
		cmds.add("-showversion");
		//cmds.add("-XX:-DoEscapeAnalysis");
		cmds.add("-ea");
		cmds.add("-cp");
		cmds.add("./lib/asm-4.2/asm-all-4.2.jar;./lib/output.jar;./bin");
		cmds.add("test.net.indiespot.struct.StructTest");

		Process proc = Runtime.getRuntime().exec(cmds.toArray(new String[cmds.size()]));
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

		System.out.println("process terminated with exit-code: " + proc.waitFor());
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

				gatherClassInfo(baos.toByteArray());
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

	private static final String RENAMED_CONSTRUCTOR_NAME = "_<init>_";
	private static Map<String, byte[]> fqcn2bytecode = new HashMap<>();
	private static Set<String> plain_struct_types = new HashSet<>();
	private static Set<String> wrapped_struct_types = new HashSet<>();
	private static Map<String, StructInfo> struct2info = new HashMap<>();
	private static Map<String, Map<String, ReturnValueStrategy>> fqcn2method2strategy = new HashMap<>();

	private static final boolean struct_rewrite_early_out = true;
	private static Map<String, Set<String>> fqcn2struct_creation_methods = new HashMap<>();
	private static Map<String, Set<String>> fqcn2struct_access_methods = new HashMap<>();

	public static void registerClass(byte[] bytecode) {
		gatherClassInfo(bytecode);
	}

	public static Map<String, byte[]> rewriteClasses() {
		for(Entry<String, byte[]> entry : fqcn2bytecode.entrySet()) {
			String className = entry.getKey();
			byte[] bytecode = entry.getValue();
			flagClassMethods(className, bytecode);
		}

		for(Entry<String, Set<String>> entry : fqcn2struct_access_methods.entrySet()) {
			String fqcn = entry.getKey();
			for(String methodName : entry.getValue()) {
				if(print_log)
					System.out.println("REWRITE: " + fqcn + "." + methodName);
			}
		}

		Map<String, byte[]> output = new HashMap<>();
		for(Entry<String, byte[]> entry : fqcn2bytecode.entrySet()) {
			String className = entry.getKey();
			byte[] bytecode1 = entry.getValue();

			if(className.startsWith("net/indiespot/struct/"))
				continue;

			if(print_log)
				System.out.println("rewriting class: " + className);

			if(className.equals(print_class)) {
				printClass(bytecode1);
			}

			byte[] bytecode2 = rewriteClass(className, bytecode1);

			if(className.equals(print_class)) {
				printClass(bytecode2);

				if(print_class_and_terminate) {
					System.exit(0);
				}
			}

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
				if(print_log)
					System.out.println("found class: " + fqcn);
				fqcn2bytecode.put(fqcn, bytecode);
			}

			// find struct.sizeof
			@Override
			public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
				if(desc.equals("L" + jvmClassName(StructType.class) + ";")) {
					if(print_log)
						System.out.println("\tfound struct: " + fqcn);
					struct2info.put(fqcn, info = new StructInfo());
					plain_struct_types.add(fqcn);
					wrapped_struct_types.add("L" + fqcn + ";");

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
					if(print_log)
						System.out.println("\t\tfound field name: " + fieldName);

					return new FieldVisitor(Opcodes.ASM4, super.visitField(access, fieldName, desc, signature, value)) {
						public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
							if(desc.equals("L" + jvmClassName(StructField.class) + ";")) {
								return new AnnotationVisitor(Opcodes.ASM4, super.visitAnnotation(desc, visible)) {
									public void visit(String name, Object value) {
										if(name.equals("offset")) {
											info.field2offset.put(fieldName, (Integer) value);
											if(print_log)
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
							if(print_log)
								System.out.println("\t\tfound method: " + methodName + methodDesc);
							if(print_log)
								System.out.println("\t\t\twith struct return value, with Copy strategy");
						}
						else if(desc.equals("L" + jvmClassName(TakeStruct.class) + ";")) {
							Map<String, ReturnValueStrategy> method2strategy = fqcn2method2strategy.get(fqcn);
							if(method2strategy == null)
								fqcn2method2strategy.put(fqcn, method2strategy = new HashMap<>());
							method2strategy.put(methodName + methodDesc, ReturnValueStrategy.PASS);
							if(print_log)
								System.out.println("\t\tfound method: " + methodName + methodDesc);
							if(print_log)
								System.out.println("\t\t\twith struct return value, with Pass strategy");
						}
						return super.visitAnnotation(desc, visible);
					}
				};
			}
		};
		new ClassReader(bytecode).accept(visitor, 0);
	}

	private static void flagClassMethods(final String fqcn, byte[] bytecode) {

		ClassWriter writer = new ClassWriter(0);
		ClassVisitor visitor = new ClassVisitor(Opcodes.ASM4, writer) {

			@Override
			public MethodVisitor visitMethod(int access, final String methodName, final String methodDesc, String signature, String[] exceptions) {
				return new MethodVisitor(Opcodes.ASM4, super.visitMethod(access, methodName, methodDesc, signature, exceptions)) {
					{
						if(print_log)
							System.out.println("checking for rewrite: " + fqcn + "." + methodName + "" + methodDesc);

						if(plain_struct_types.contains(fqcn)) {
							this.flagRewriteMethod(false);
						}

						for(String wrappedStructType : wrapped_struct_types) {
							if(methodDesc.contains(wrappedStructType)) {
								this.flagRewriteMethod(false);
							}
						}
					}

					@Override
					public void visitTypeInsn(int opcode, String type) {
						if(opcode == NEW) {
							if(plain_struct_types.contains(type)) {
								this.flagRewriteMethod(true);
							}
						}
						if(opcode == INSTANCEOF) {
							if(plain_struct_types.contains(type)) {
								this.flagRewriteMethod(false);
							}
						}
						if(opcode == ANEWARRAY) {
							if(plain_struct_types.contains(type)) {
								this.flagRewriteMethod(false);
							}
						}
					}

					@Override
					public void visitFieldInsn(int opcode, String owner, String name, String desc) {
						if(print_log)
							System.out.println("\t" + owner + "." + name + " " + desc);
						if(plain_struct_types.contains(owner)) {
							this.flagRewriteMethod(false);
						}
						for(String wrappedStructType : wrapped_struct_types) {
							if(desc.contains(wrappedStructType)) {
								this.flagRewriteMethod(false);
							}
						}

						super.visitFieldInsn(opcode, owner, name, desc);
					}

					@Override
					public void visitMethodInsn(int opcode, String owner, String name, String desc) {
						if(plain_struct_types.contains(owner)) {
							this.flagRewriteMethod(false);
						}
						for(String wrappedStructType : wrapped_struct_types) {
							if(desc.contains(wrappedStructType)) {
								this.flagRewriteMethod(false);
							}
						}

						super.visitMethodInsn(opcode, owner, name, desc);
					}

					@Override
					public void visitLdcInsn(Object cst) {
						if(cst instanceof Type) {
							if(plain_struct_types.contains(((Type) cst).getInternalName())) {
								this.flagRewriteMethod(false);
							}
						}

						super.visitLdcInsn(cst);
					}

					private void flagRewriteMethod(boolean forNew) {
						if(true) {
							Set<String> methods = fqcn2struct_access_methods.get(fqcn);
							if(methods == null)
								fqcn2struct_access_methods.put(fqcn, methods = new HashSet<>());
							if(methods.add(methodName + methodDesc))
								if(print_log)
									System.out.println("flagging for rewrite: " + fqcn + "." + methodName + "" + methodDesc);
						}

						if(forNew) {
							Set<String> methods = fqcn2struct_creation_methods.get(fqcn);
							if(methods == null)
								fqcn2struct_creation_methods.put(fqcn, methods = new HashSet<>());
							methods.add(methodName + methodDesc);
						}
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
				if(print_log)
					System.out.println("\tfield1: " + name + " " + desc);

				if(struct2info.containsKey(fqcn)) {
					if((access & ACC_STATIC) == 0) {
						if(print_log)
							System.out.println("\tremoved struct field");
						return null; // remove instance fields
					}
				}

				if(wrapped_struct_types.contains(desc))
					desc = wrapped_struct_flag;
				if(print_log)
					System.out.println("\tfield2: " + name + " " + desc);
				String finalFieldDesc = desc.replace(wrapped_struct_flag, "I");
				return super.visitField(access, name, finalFieldDesc, signature, value);
			}

			@Override
			public MethodVisitor visitMethod(int access, String methodName, String methodDesc, String signature, String[] exceptions) {
				final String origMethodName = methodName;
				final String origMethodDesc = methodDesc;

				if(print_log)
					System.out.println("\tmethod1: " + methodName + " " + methodDesc);

				String returnsStructType = null;
				for(String struct : struct2info.keySet()) {
					if(methodDesc.endsWith(")L" + struct + ";"))
						returnsStructType = struct;
					methodDesc = methodDesc.replace("L" + struct + ";", wrapped_struct_flag);
				}
				final String _returnsStructType = returnsStructType;

				if(struct2info.containsKey(fqcn)) {
					if(methodName.equals("<init>"))
						methodName = RENAMED_CONSTRUCTOR_NAME;

					if((access & ACC_STATIC) == 0) {
						// make instance methods static
						// add 'this' as first parameter
						access |= ACC_STATIC;
						methodDesc = "(" + wrapped_struct_flag + methodDesc.substring(1);
					}
				}

				if(print_log)
					System.out.println("\tmethod2: " + methodName + " " + methodDesc);

				String finalMethodName = methodName.replace(wrapped_struct_flag, "I");
				String finalMethodDesc = methodDesc.replace(wrapped_struct_flag, "I");
				final MethodVisitor mv = super.visitMethod(access, finalMethodName, finalMethodDesc, signature, exceptions);

				if(struct_rewrite_early_out) {
					// do we need to rewrite this method?
					Set<String> rewriteMethods = fqcn2struct_access_methods.get(fqcn);
					boolean earlyOut = (rewriteMethods == null) || !rewriteMethods.contains(origMethodName + origMethodDesc);
					if(print_log)
						System.out.println("early out for rewrite? [" + earlyOut + "] " + fqcn + "." + origMethodName + origMethodDesc);
					if(earlyOut)
						return mv; // nope!
				}

				final String _methodName = methodName;

				final FlowAnalysisMethodVisitor flow = new FlowAnalysisMethodVisitor(mv, access, fqcn, methodName, methodDesc, signature, exceptions);
				return new MethodVisitor(Opcodes.ASM4, flow) {

					@Override
					public void visitCode() {
						if(_returnsStructType != null) {
							String msg = "";
							msg += "must define how struct return values are handled: ";
							msg += "\n\t\t" + fqcn + "." + _methodName + origMethodDesc;

							Map<String, ReturnValueStrategy> method2strategy = fqcn2method2strategy.get(fqcn);
							if(method2strategy == null)
								throw new IllegalStateException(msg);
							if(!method2strategy.containsKey(_methodName + origMethodDesc))
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
									local[i] = wrapped_struct_flag;
							}
						}
						if(stack != null) {
							for(int i = 0; i < stack.length; i++) {
								if(struct2info.containsKey(stack[i]))
									stack[i] = wrapped_struct_flag;
							}
						}

						super.visitFrame(type, nLocal, local, nStack, stack);
					}

					@Override
					public void visitTypeInsn(int opcode, String type) {
						if(opcode == NEW) {
							if(struct2info.containsKey(type)) {
								super.visitIntInsn(Opcodes.BIPUSH, struct2info.get(type).sizeof);
								super.visitMethodInsn(Opcodes.INVOKESTATIC, StructBuild.jvmClassName(StructMemory.class), "allocate", "(I)" + wrapped_struct_flag);
								return;
							}
						}
						else if(opcode == ANEWARRAY) {
							if(struct2info.containsKey(type)) {
								super.visitIntInsn(Opcodes.BIPUSH, struct2info.get(type).sizeof);
								super.visitMethodInsn(Opcodes.INVOKESTATIC, StructBuild.jvmClassName(StructMemory.class), "allocateArray", "(II)[" + wrapped_struct_flag);
								flow.stack.popEQ(VarType.STRUCT_ARRAY);
								flow.stack.push(VarType.STRUCT_ARRAY);
								return;
							}
						}
						else if(opcode == CHECKCAST) {
							if(flow.stack.peek() == VarType.STRUCT) {
								//super.visitInsn(POP); // ref
								//super.visitMethodInsn(Opcodes.INVOKESTATIC, jvmClassName(StructMemory.class), "execCheckcastInsn", "()V");
								return;
							}
							if(flow.stack.peek() == VarType.STRUCT_ARRAY) {
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
								super.visitMethodInsn(INVOKESTATIC, StructBuild.jvmClassName(StructMemory.class), "write", "(" + wrapped_struct_flag + type + "I)V");
								return;
							}

						}
						else if(opcode == GETFIELD) {
							if(struct2info.containsKey(owner)) {
								StructInfo info = struct2info.get(owner);
								int offset = info.field2offset.get(name).intValue();
								String type = info.field2type.get(name);
								super.visitIntInsn(BIPUSH, offset);
								super.visitMethodInsn(INVOKESTATIC, StructBuild.jvmClassName(StructMemory.class), "read", "(" + wrapped_struct_flag + "I)" + type);
								return;
							}
						}

						if(wrapped_struct_types.contains(desc)) {
							desc = wrapped_struct_flag;
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
							desc = desc.replace(structType, plain_struct_flag);
						}

						if(struct2info.containsKey(fqcn) && _methodName.equals(RENAMED_CONSTRUCTOR_NAME)) {
							// remove Object.super() from any Struct constructor
							if(opcode == INVOKESPECIAL && name.equals("<init>") && desc.equals("()V")) {
								super.visitInsn(POP);
								return;
							}
						}

						if(struct2info.containsKey(owner)) {
							if(opcode == INVOKESPECIAL) {
								if(name.equals("<init>")) {
									name = RENAMED_CONSTRUCTOR_NAME;

									// add 'this' as first parameter
									opcode = INVOKESTATIC;
									desc = "(" + wrapped_struct_flag + desc.substring(1);
								}
							}
							else if(opcode == INVOKEVIRTUAL) {
								// add 'this' as first parameter
								opcode = INVOKESTATIC;
								desc = "(" + wrapped_struct_flag + desc.substring(1);
							}
						}

						if(owner.equals(StructBuild.jvmClassName(StructUtil.class))) {
							if(name.equals("getPointer") && desc.equals("(Ljava/lang/Object;)J")) {
								if(flow.stack.peek() == VarType.STRUCT) {
									owner = StructBuild.jvmClassName(StructMemory.class);
									name = "handle2pointer";
									desc = "(" + wrapped_struct_flag + ")J";
								}
							}
							else if(name.equals("isReachable") && desc.equals("(Ljava/lang/Object;)Z")) {
								if(flow.stack.peek() == VarType.STRUCT) {
									owner = StructBuild.jvmClassName(StructMemory.class);
									name = "isValid";
									desc = "(" + wrapped_struct_flag + ")Z";
								}
							}
							else if(name.equals("map") && desc.equals("(Ljava/lang/Class;Ljava/nio/ByteBuffer;)[Ljava/lang/Object;")) {
								if(flow.stack.peek() == VarType.REFERENCE) {
									if(flow.stack.peek(1) == VarType.STRUCT_TYPE) {
										// ..., STRUCT_TYPE, REFERENCE
										flow.visitInsn(SWAP);
										// ..., REFERENCE, STRUCT_TYPE
										flow.visitInsn(POP);
										// ..., REFERENCE
										flow.visitIntInsn(BIPUSH, struct2info.get(lastLdcStruct).sizeof);
										lastLdcStruct = null;
										// ..., REFERENCE, SIZEOF
										owner = StructBuild.jvmClassName(StructMemory.class);
										name = "mapArray";
										desc = "(Ljava/nio/ByteBuffer;I)[" + wrapped_struct_flag;
									}
								}
							}
						}

						super.visitMethodInsn(opcode, owner, name, desc);

						if(returnsStructType != null) {
							Map<String, ReturnValueStrategy> method2strategy = fqcn2method2strategy.get(owner);
							if(method2strategy == null)
								throw new IllegalStateException();

							ReturnValueStrategy strategy = method2strategy.get(name + origDesc);
							if(print_log)
								System.out.println("\t\t\treturn value strategy: " + strategy);

							if(strategy == ReturnValueStrategy.PASS) {
								// no-op
							}
							else if(strategy == ReturnValueStrategy.COPY) {
								super.visitIntInsn(BIPUSH, struct2info.get(returnsStructType).sizeof);
								super.visitMethodInsn(INVOKESTATIC, StructBuild.jvmClassName(StructMemory.class), "allocateCopy", "(" + wrapped_struct_flag + "I)" + wrapped_struct_flag);
							}
							else {
								throw new IllegalStateException();
							}
						}
					}

					@Override
					public void visitInsn(int opcode) {
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

					private String lastLdcStruct;

					@Override
					public void visitLdcInsn(Object cst) {
						if(cst instanceof Type) {
							if(plain_struct_types.contains(((Type) cst).getInternalName())) {
								lastLdcStruct = ((Type) cst).getInternalName();
								cst = Type.getType(wrapped_struct_flag);
							}
						}

						super.visitLdcInsn(cst);
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