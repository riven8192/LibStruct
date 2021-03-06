package net.indiespot.struct.transform;

import static org.objectweb.asm.Opcodes.*;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import net.indiespot.struct.StructInfo;
import net.indiespot.struct.cp.CopyStruct;
import net.indiespot.struct.cp.Struct;
import net.indiespot.struct.cp.StructConfig;
import net.indiespot.struct.cp.StructType;
import net.indiespot.struct.cp.TakeStruct;
import net.indiespot.struct.runtime.StructAllocationStack;
import net.indiespot.struct.runtime.StructGC;
import net.indiespot.struct.runtime.StructMemory;
import net.indiespot.struct.runtime.StructThreadLocalStack;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceClassVisitor;

public class StructEnv {
	public static final boolean SAFETY_FIRST;
	public static final boolean PRINT_LOG;
	public static final boolean MEMORY_BASE_OFFSET;

	static {
		SAFETY_FIRST = StructConfig.parseVmArg(StructEnv.class, "SAFETY_FIRST", 0, false) != 0L;
		PRINT_LOG = StructConfig.parseVmArg(StructEnv.class, "PRINT_LOG", 0, false) != 0L;
		MEMORY_BASE_OFFSET = StructConfig.parseVmArg(StructEnv.class, "MEMORY_BASE_OFFSET", 0, false) != 0L;
	}

	public static final String plain_struct_flag = "$truct";
	public static final String wrapped_struct_flag = "L" + plain_struct_flag + ";";
	public static final String array_wrapped_struct_flag = "[L" + plain_struct_flag + ";";
	private static final String RENAMED_CONSTRUCTOR_NAME = "_<init>_";

	public static boolean isStructReferenceWide() {
		return true;
	}

	private static Set<String> plain_struct_types = new HashSet<>();
	private static Set<String> wrapped_struct_types = new HashSet<>();
	private static Set<String> array_wrapped_struct_types = new HashSet<>();
	private static Map<String, StructInfo> struct2info = new HashMap<>();

	private static final boolean struct_rewrite_early_out = true;
	private static volatile boolean is_rewriting = false;

	public static void addStruct(StructInfo info) {
		if (is_rewriting)
			throw new IllegalStateException("cannot add struct definition when classes have been rewritten already");
		if (struct2info.put(info.fqcn, info) != null)
			throw new IllegalStateException("duplicate struct definition: " + info.fqcn);
		plain_struct_types.add(info.fqcn);
		wrapped_struct_types.add("L" + info.fqcn + ";");
		array_wrapped_struct_types.add("[L" + info.fqcn + ";");
	}

	public static void linkStructs() {
		for (StructInfo info : struct2info.values())
			info.validate();
	}

	private static enum ReturnValueStrategy {
		COPY, PASS
	}

	private static class ClassInfo {
		final Set<String> fieldsWithStructType = new HashSet<>();
		final Set<String> methodsWithStructAccess = new HashSet<>();
		final Set<String> methodsWithStructCreation = new HashSet<>();
		final Map<String, Integer> methodNameDesc2localCount = new HashMap<>();

		public boolean needsRewrite() {
			return !fieldsWithStructType.isEmpty() || !methodsWithStructAccess.isEmpty() || !methodsWithStructCreation.isEmpty();
		}

		private void analyze(final String fqcn, byte[] bytecode) {

			if (PRINT_LOG)
				System.out.println("analyzing class: " + fqcn);

			ClassWriter writer = new ClassWriter(0);
			ClassVisitor visitor = new ClassVisitor(Opcodes.ASM5, writer) {
				@Override
				public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
					if (plain_struct_types.contains(fqcn)) {
						if (!superName.equals("java/lang/Object")) {
							throw new IllegalStateException("struct [" + fqcn + "] must have java.lang.Object as super-type");
						}
						if (interfaces != null && interfaces.length != 0) {
							throw new IllegalStateException("struct [" + fqcn + "] must not implement any interfaces");
						}
					}

					super.visit(version, access, name, signature, superName, interfaces);
				}

				@Override
				public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
					if (desc.equals("L" + StructEnv.jvmClassName(StructType.class) + ";")) {
						if (!plain_struct_types.contains(fqcn)) {
							throw new IllegalStateException("encountered undefined struct: " + fqcn);
						}
					}
					return super.visitAnnotation(desc, visible);
				}

				@Override
				public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
					if (wrapped_struct_types.contains(desc)) {
						this.flagRewriteField(name, desc);
					}
					if (array_wrapped_struct_types.contains(desc)) {
						this.flagRewriteField(name, desc);
					}

					return super.visitField(access, name, desc, signature, value);
				}

				private void flagRewriteField(String name, String desc) {
					if (fieldsWithStructType.add(name + desc))
						if (PRINT_LOG)
							System.out.println("\tflagging field: " + name + "" + desc);
				}

				@Override
				public MethodVisitor visitMethod(int access, final String methodName, final String methodDesc, String signature, String[] exceptions) {
					return new MethodVisitor(Opcodes.ASM5, super.visitMethod(access, methodName, methodDesc, signature, exceptions)) {
						{
							if (PRINT_LOG)
								System.out.println("\tchecking method for rewrite: " + methodName + "" + methodDesc);

							if (plain_struct_types.contains(fqcn)) {
								this.flagRewriteMethod(false);
							}

							for (String wrappedStructType : wrapped_struct_types) {
								if (methodDesc.contains(wrappedStructType)) {
									this.flagRewriteMethod(false);
								}
							}
						}

						@Override
						public void visitTypeInsn(int opcode, String type) {
							if (opcode == NEW) {
								if (plain_struct_types.contains(type)) {
									this.flagRewriteMethod(true);
								}
							}
							if (opcode == INSTANCEOF) {
								if (plain_struct_types.contains(type)) {
									this.flagRewriteMethod(false);
								}
							}
							if (opcode == ANEWARRAY) {
								if (plain_struct_types.contains(type)) {
									this.flagRewriteMethod(true);
								}
							}
						}

						@Override
						public void visitFieldInsn(int opcode, String owner, String name, String desc) {
							if (plain_struct_types.contains(owner)) {
								this.flagRewriteMethod(false);
							}
							for (String wrappedStructType : wrapped_struct_types) {
								if (desc.contains(wrappedStructType)) {
									this.flagRewriteMethod(false);
								}
							}

							super.visitFieldInsn(opcode, owner, name, desc);
						}

						@Override
						public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
							if (plain_struct_types.contains(owner)) {
								this.flagRewriteMethod(false);
							}
							for (String wrappedStructType : wrapped_struct_types) {
								if (desc.contains(wrappedStructType)) {
									this.flagRewriteMethod(false);
								}
							}
							if (owner.equals(jvmClassName(Struct.class))) {
								this.flagRewriteMethod(false);
							}

							super.visitMethodInsn(opcode, owner, name, desc, itf);
						}

						@Override
						public void visitLdcInsn(Object cst) {
							if (cst instanceof Type) {
								if (plain_struct_types.contains(((Type) cst).getInternalName())) {
									this.flagRewriteMethod(false);
								}
							}

							super.visitLdcInsn(cst);
						}

						private void flagRewriteMethod(boolean forStructCreation) {
							if (methodsWithStructAccess.add(methodName + methodDesc))
								if (PRINT_LOG)
									System.out.println("\t\tflagged for rewrite");

							if (forStructCreation) {
								methodsWithStructCreation.add(methodName + methodDesc);
							}
						}

						@Override
						public void visitMaxs(int maxStack, int maxLocals) {
							methodNameDesc2localCount.put(methodName + methodDesc, Integer.valueOf(maxLocals));
							super.visitMaxs(maxStack, maxLocals);
						}
					};
				}
			};
			new ClassReader(bytecode).accept(visitor, 0);
		}
	}

	private static final Map<Integer, String> _aload2type = new HashMap<>();
	private static final Map<Integer, String> _astore2type = new HashMap<>();
	static {
		_aload2type.put(Opcodes.LALOAD, "J");
		_aload2type.put(Opcodes.DALOAD, "D");
		_aload2type.put(Opcodes.IALOAD, "I");
		_aload2type.put(Opcodes.FALOAD, "F");
		_aload2type.put(Opcodes.BALOAD, "B");
		_aload2type.put(Opcodes.CALOAD, "C");
		_aload2type.put(Opcodes.SALOAD, "S");

		_astore2type.put(Opcodes.LASTORE, "J");
		_astore2type.put(Opcodes.DASTORE, "D");
		_astore2type.put(Opcodes.IASTORE, "I");
		_astore2type.put(Opcodes.FASTORE, "F");
		_astore2type.put(Opcodes.BASTORE, "B");
		_astore2type.put(Opcodes.CASTORE, "C");
		_astore2type.put(Opcodes.SASTORE, "S");
	}

	public static byte[] rewriteClass(final String fqcn, byte[] bytecode) {
		is_rewriting = true;

		final ClassInfo info = new ClassInfo();
		info.analyze(fqcn, bytecode);
		if (!info.needsRewrite())
			return null;

		if (StructEnv.PRINT_LOG) {
			System.out.println("StructEnv.rewrite INPUT [" + fqcn + "]:");
			int flags = 0;
			ClassReader cr = new ClassReader(bytecode);
			cr.accept(new TraceClassVisitor(null, new Textifier(), new PrintWriter(System.out)), flags);
		}

		final ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
			protected String getCommonSuperClass(String type1, String type2) {
				String type3 = super.getCommonSuperClass(type1, type2);
				System.out.println("StructEnv.rewriteClass ClassWriter.getCommonSuperClass(" + type1 + ", " + type2 + ") -> " + type3);
				return type3;
			}
		};

		final Map<String, Set<String>> final2origMethods = new HashMap<>();

		final String[] currentMethodName = new String[1];
		final String[] currentMethodDesc = new String[1];

		ClassVisitor visitor = new ClassVisitor(Opcodes.ASM5, writer) {

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
				if (PRINT_LOG)
					System.out.println("\tfield1: " + name + " " + desc);

				if (struct2info.containsKey(fqcn)) {
					if ((access & ACC_STATIC) == 0) {
						if (PRINT_LOG)
							System.out.println("\tremoved struct field");
						return null; // remove instance fields
					}
				}

				if (wrapped_struct_types.contains(desc))
					desc = wrapped_struct_flag;
				if (array_wrapped_struct_types.contains(desc))
					desc = array_wrapped_struct_flag;
				if (PRINT_LOG)
					System.out.println("\tfield2: " + name + " " + desc);
				String finalFieldDesc = desc.replace(wrapped_struct_flag, "J");
				return super.visitField(access, name, finalFieldDesc, signature, value);
			}

			@Override
			public MethodVisitor visitMethod(int access, String methodName, String methodDesc, String signature, String[] exceptions) {
				currentMethodName[0] = methodName;
				currentMethodDesc[0] = methodDesc;

				final String origMethodName = methodName;
				final String origMethodDesc = methodDesc;

				if (PRINT_LOG)
					System.out.println("\tmethod1: " + methodName + " " + methodDesc);

				String returnsStructType = null;
				for (String struct : struct2info.keySet()) {
					if (methodDesc.endsWith(")L" + struct + ";"))
						returnsStructType = struct;
					methodDesc = methodDesc.replace("L" + struct + ";", wrapped_struct_flag);
				}
				final String _returnsStructType = returnsStructType;

				if (struct2info.containsKey(fqcn)) {
					if (methodName.equals("<init>"))
						methodName = RENAMED_CONSTRUCTOR_NAME;

					if ((access & ACC_STATIC) == 0) {
						// make instance methods static
						// add 'this' as first parameter
						access |= ACC_STATIC;
						methodDesc = "(" + wrapped_struct_flag + methodDesc.substring(1);
					}
				}

				if (PRINT_LOG)
					System.out.println("\tmethod2: " + methodName + " " + methodDesc);

				String finalMethodName = methodName.replace(wrapped_struct_flag, "J");
				String finalMethodDesc = methodDesc.replace(wrapped_struct_flag, "J");

				{
					String key = finalMethodName + " " + finalMethodDesc;
					Set<String> origMethods = final2origMethods.get(key);
					if (origMethods == null)
						final2origMethods.put(key, origMethods = new TreeSet<>());
					origMethods.add(origMethodName + " " + origMethodDesc);
				}

				final MethodVisitor mv = super.visitMethod(access, finalMethodName, finalMethodDesc, signature, exceptions);

				if (struct_rewrite_early_out) {
					// do we need to rewrite this method?
					boolean hasStructAccess = info.methodsWithStructAccess.contains(origMethodName + origMethodDesc);
					if (PRINT_LOG)
						System.out.println("\t\tearly out for rewrite? [" + !hasStructAccess + "]");
					if (!hasStructAccess)
						return mv; // nope!
				}
				final boolean hasStructCreation = info.methodsWithStructCreation.contains(origMethodName + origMethodDesc);
				Integer localCountObj = info.methodNameDesc2localCount.get(origMethodName + origMethodDesc);
				final int origLocalvarSlots = (localCountObj == null) ? 0 : localCountObj.intValue();
				final int usedLocalvarSlots = (localCountObj == null) ? 0 : origLocalvarSlots == -1 ? 0 ://
				origLocalvarSlots + //
						(hasStructCreation ? 1 : 0) + // TL-SAS
						(StructEnv.SAFETY_FIRST ? 4 : 0); // check suspicious field assignment

				final String _methodName = methodName;
				final int _access = access;
				final FlowAnalysisMethodVisitor flow = new FlowAnalysisMethodVisitor(usedLocalvarSlots, mv, access, fqcn, methodName, methodDesc, signature, exceptions);
				return new MethodVisitor(Opcodes.ASM5, flow) {
					private ReturnValueStrategy strategy;

					@Override
					public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
						if (desc.equals("L" + jvmClassName(CopyStruct.class) + ";")) {
							strategy = ReturnValueStrategy.COPY;
							if (PRINT_LOG)
								System.out.println("\t\t\twith struct return value, with Copy strategy");
						} else if (desc.equals("L" + jvmClassName(TakeStruct.class) + ";")) {
							strategy = ReturnValueStrategy.PASS;
							if (PRINT_LOG)
								System.out.println("\t\t\twith struct return value, with Pass strategy");
						}
						return super.visitAnnotation(desc, visible);
					}

					@Override
					public void visitCode() {
						if (_returnsStructType == null) {
							if (strategy != null) {
								String msg = "";
								msg += "@CopyStruct and @TakeStruct must only be used with struct return values";
								msg += "\n\t\t" + fqcn + "." + _methodName + origMethodDesc;

								throw new IllegalStateException(msg);
							}
						} else {
							if (strategy == null) {
								if ((_access & ACC_SYNTHETIC) != 0) {
									strategy = ReturnValueStrategy.PASS;
									if (PRINT_LOG)
										System.out.println("\t\t\twith struct return value, using fallback Pass strategy due to synthetic method");
								}
							}
							if (strategy == null) {
								String msg = "";
								msg += "must define how struct return values are handled: ";
								msg += "\n\t\t" + fqcn + "." + _methodName + origMethodDesc;

								throw new IllegalStateException(msg);
							}
						}

						super.visitCode();

						if (hasStructCreation) {
							// ...
							super.visitMethodInsn(INVOKESTATIC, jvmClassName(StructThreadLocalStack.class), "saveStack", "()L" + StructAllocationStack.class.getName().replace('.', '/') + ";", false);
							// ..., sas
							super.visitVarInsn(ASTORE, origLocalvarSlots);
						}
					}

					@Override
					public void visitInsn(int opcode) {
						if (hasStructCreation) {
							switch (opcode) {
							case RETURN:
							case ARETURN:
							case IRETURN:
							case FRETURN:
							case LRETURN:
							case DRETURN:
								super.visitVarInsn(ALOAD, origLocalvarSlots);
								super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, StructEnv.jvmClassName(StructAllocationStack.class), "restore", "()I", false);
								super.visitInsn(Opcodes.POP);
								break;
							}
						}

						switch (opcode) {
						case BALOAD:
						case SALOAD:
						case CALOAD:
						case IALOAD:
						case FALOAD:
						case DALOAD:
						case LALOAD:
							if (flow.stack.peek(1) == VarType.POINTER64) {
								flow.stack.popEQ(VarType.INT); // index
								flow.stack.popEQ(VarType.POINTER64); // pointer
								flow.stack.popEQ(VarType.POINTER64);

								flow.stack.push(VarType.MISC);
								flow.stack.push(VarType.MISC);
								flow.stack.push(VarType.INT);

								String arrayElemType = _aload2type.get(Integer.valueOf(opcode));

								flow.visitMethodInsn(Opcodes.INVOKESTATIC, "net/indiespot/struct/runtime/StructMemory", arrayElemType.toLowerCase() + "aget", "(JI)" + arrayElemType, false);
								return;
							}
							break;

						case BASTORE:
						case SASTORE:
						case CASTORE:
						case IASTORE:
						case FASTORE:
						case DASTORE:
						case LASTORE:
							boolean isWide = (opcode == DASTORE || opcode == LASTORE);
							if (flow.stack.peek(isWide ? 3 : 2) == VarType.POINTER64) {
								VarType valueType1 = flow.stack.pop(); // value
								VarType valueType2 = (isWide ? flow.stack.pop() : null); // value
								flow.stack.popEQ(VarType.INT); // index

								flow.stack.popEQ(VarType.POINTER64);
								flow.stack.popEQ(VarType.POINTER64);

								flow.stack.push(VarType.MISC);
								flow.stack.push(VarType.MISC);
								flow.stack.push(VarType.INT);
								if (isWide)
									flow.stack.push(valueType2);
								flow.stack.push(valueType1);

								String arrayElemType = _astore2type.get(Integer.valueOf(opcode));

								flow.visitMethodInsn(Opcodes.INVOKESTATIC, "net/indiespot/struct/runtime/StructMemory", arrayElemType.toLowerCase() + "aput", "(JI" + arrayElemType + ")V", false);
								return;
							}
							break;
						}

						if (opcode == ARETURN && flow.stack.peek() == VarType.STRUCT_LO) {
							if (strategy == null)
								throw new IllegalStateException();

							if (PRINT_LOG)
								System.out.println("\t\t\treturn value strategy: " + strategy);

							if (strategy == ReturnValueStrategy.PASS) {
								// no-op
							} else if (strategy == ReturnValueStrategy.COPY) {
								super.visitIntInsn(SIPUSH, struct2info.get(_returnsStructType).sizeof);
								super.visitIntInsn(BIPUSH, StructMemory.DEFAULT_ALIGNMENT);
								super.visitMethodInsn(INVOKESTATIC, StructEnv.jvmClassName(StructMemory.class), "allocateCopy", "(" + wrapped_struct_flag + "II)" + wrapped_struct_flag, false);
							} else {
								throw new IllegalStateException();
							}
						}

						super.visitInsn(opcode);
					}

					@Override
					public void visitLocalVariable(String name, String desc, String signature, Label start, Label end, int index) {
						if (array_wrapped_struct_types.contains(desc))
							desc = array_wrapped_struct_flag;
						else if (wrapped_struct_types.contains(desc))
							desc = wrapped_struct_flag;
						super.visitLocalVariable(name, desc, signature, start, end, index);
					}

					@Override
					public void visitFrame(int type, int nLocal, Object[] local, int nStack, Object[] stack) {
						// System.out.println("visitFrame: local=" +
						// Arrays.toString(local) + ", stack=" +
						// Arrays.toString(stack));
						if (local != null) {
							for (int i = 0; i < local.length; i++) {
								if (array_wrapped_struct_types.contains(local[i]))
									local[i] = array_wrapped_struct_flag;
								else if (wrapped_struct_types.contains(local[i]))
									local[i] = wrapped_struct_flag;
							}
						}
						if (stack != null) {
							for (int i = 0; i < stack.length; i++) {
								if (array_wrapped_struct_types.contains(stack[i]))
									stack[i] = array_wrapped_struct_flag;
								else if (wrapped_struct_types.contains(stack[i]))
									stack[i] = wrapped_struct_flag;
							}
						}

						super.visitFrame(type, nLocal, local, nStack, stack);
					}

					@Override
					public void visitTypeInsn(int opcode, String type) {
						if (opcode == NEW) {
							if (struct2info.containsKey(type)) {
								super.visitVarInsn(ALOAD, info.methodNameDesc2localCount.get(origMethodName + origMethodDesc).intValue());
								super.visitIntInsn(Opcodes.SIPUSH, struct2info.get(type).sizeof);
								super.visitIntInsn(Opcodes.BIPUSH, StructMemory.DEFAULT_ALIGNMENT);
								if (struct2info.get(type).disableClearMemory)
									super.visitMethodInsn(Opcodes.INVOKESTATIC, StructEnv.jvmClassName(StructMemory.class), "malloc", "(L" + StructEnv.jvmClassName(StructAllocationStack.class) + ";II)" + wrapped_struct_flag, false);
								else
									super.visitMethodInsn(Opcodes.INVOKESTATIC, StructEnv.jvmClassName(StructMemory.class), "calloc", "(L" + StructEnv.jvmClassName(StructAllocationStack.class) + ";II)" + wrapped_struct_flag, false);
								return;
							}
						} else if (opcode == ANEWARRAY) {
							if (struct2info.containsKey(type)) {
								super.visitVarInsn(ALOAD, info.methodNameDesc2localCount.get(origMethodName + origMethodDesc).intValue());
								super.visitInsn(Opcodes.SWAP);
								super.visitIntInsn(Opcodes.SIPUSH, struct2info.get(type).sizeof);
								super.visitIntInsn(Opcodes.BIPUSH, StructMemory.DEFAULT_ALIGNMENT);
								// stack,length,sizeof,alignment
								super.visitMethodInsn(Opcodes.INVOKESTATIC, StructEnv.jvmClassName(StructMemory.class), "allocateArray", "(L" + jvmClassName(StructAllocationStack.class) + ";III)" + array_wrapped_struct_flag, false);
								flow.stack.popEQ(VarType.STRUCT_ARRAY);
								flow.stack.push(VarType.STRUCT_ARRAY);
								return;
							}
						} else if (opcode == CHECKCAST) {
							if (flow.stack.peek() == VarType.STRUCT_LO) {
								return;
							}
							if (flow.stack.peek() == VarType.STRUCT_ARRAY) {
								return;
							}
						}

						super.visitTypeInsn(opcode, type);
					}

					@Override
					public void visitFieldInsn(int opcode, String owner, String name, String desc) {
						if (StructEnv.SAFETY_FIRST) {
							if (opcode == PUTFIELD || opcode == PUTSTATIC) {
								if (wrapped_struct_types.contains(desc)) {
									if (plain_struct_types.contains(owner)) {
										int base = origLocalvarSlots + (hasStructCreation ? 1 : 0);
										super.visitVarInsn(ASTORE, 1 + base);
										super.visitVarInsn(ASTORE, 0 + base);
										super.visitVarInsn(ALOAD, 0 + base);
										super.visitVarInsn(ALOAD, 1 + base);
										super.visitIntInsn(BIPUSH, opcode == PUTSTATIC ? 1 : 0);
										super.visitLdcInsn(owner + "." + name);
										super.visitMethodInsn(INVOKESTATIC, StructEnv.jvmClassName(StructMemory.class), "checkFieldAssignment", "(" + wrapped_struct_flag + "" + wrapped_struct_flag + "ZLjava/lang/String;)V", false);
										super.visitVarInsn(ALOAD, 0 + base);
										super.visitVarInsn(ALOAD, 1 + base);
									} else {
										super.visitInsn(DUP);
										super.visitIntInsn(BIPUSH, opcode == PUTSTATIC ? 1 : 0);
										super.visitLdcInsn(owner + "." + name);
										super.visitMethodInsn(INVOKESTATIC, StructEnv.jvmClassName(StructMemory.class), "checkFieldAssignment", "(" + wrapped_struct_flag + "ZLjava/lang/String;)V", false);
									}
								}
							}
						}

						if (opcode == PUTFIELD) {
							if (struct2info.containsKey(owner)) {
								StructInfo info = struct2info.get(owner);
								int offset = info.field2offset.get(name).intValue();
								boolean embed = info.field2embed.get(name).booleanValue();
								String type = info.field2type.get(name);

								if (embed) {
									throw new IllegalStateException("cannot assign to embedded struct field");
								}

								if (type.startsWith("[") && type.length() == 2) {
									throw new IllegalStateException("cannot assign to embedded arrays field");
								}

								String methodName, paramType, returnType;
								if (wrapped_struct_types.contains(type)) {
									methodName = "$put";
									paramType = wrapped_struct_flag;
									returnType = "V";
								} else {
									methodName = type.toLowerCase() + "put";
									paramType = type;
									returnType = "V";
								}

								super.visitIntInsn(SIPUSH, offset);
								super.visitMethodInsn(INVOKESTATIC, StructEnv.jvmClassName(StructMemory.class), methodName, "(" + wrapped_struct_flag + paramType + "I)" + returnType, false);
								return;
							}
						} else if (opcode == GETFIELD) {
							if (struct2info.containsKey(owner)) {
								StructInfo info = struct2info.get(owner);
								int offset = info.field2offset.get(name).intValue();
								boolean embed = info.field2embed.get(name).booleanValue();
								String type = info.field2type.get(name);

								if (embed) {
									flow.stack.popEQ(VarType.STRUCT_LO);
									flow.stack.popEQ(VarType.STRUCT_HI);

									flow.stack.push(VarType.MISC);
									flow.stack.push(VarType.MISC);

									super.visitIntInsn(SIPUSH, offset);
									super.visitInsn(I2L);
									super.visitInsn(LADD);

									flow.stack.popEQ(VarType.MISC);
									flow.stack.popEQ(VarType.MISC);

									flow.stack.push(VarType.STRUCT_HI);
									flow.stack.push(VarType.STRUCT_LO);
									return;
								}

								if (type.startsWith("[") && type.length() == 2) {
									flow.stack.popEQ(VarType.STRUCT_LO);
									flow.stack.popEQ(VarType.STRUCT_HI);

									flow.stack.push(VarType.MISC);
									flow.stack.push(VarType.MISC);

									super.visitIntInsn(SIPUSH, offset);
									super.visitInsn(I2L);
									super.visitInsn(LADD);

									flow.stack.popEQ(VarType.MISC);
									flow.stack.popEQ(VarType.MISC);

									flow.stack.push(VarType.POINTER64);
									flow.stack.push(VarType.POINTER64);
									return;
								}

								String methodName, paramType, returnType;
								if (wrapped_struct_types.contains(type)) {
									methodName = "$get";
									paramType = wrapped_struct_flag;
									returnType = wrapped_struct_flag;
								} else {
									methodName = type.toLowerCase() + "get";
									paramType = wrapped_struct_flag;
									returnType = type;
								}

								super.visitIntInsn(SIPUSH, offset);
								super.visitMethodInsn(INVOKESTATIC, StructEnv.jvmClassName(StructMemory.class), methodName, "(" + paramType + "I)" + returnType, false);
								return;
							}
						}

						if (wrapped_struct_types.contains(desc)) {
							desc = wrapped_struct_flag;
						}
						if (array_wrapped_struct_types.contains(desc)) {
							desc = array_wrapped_struct_flag;
						}

						super.visitFieldInsn(opcode, owner, name, desc);
					}

					@Override
					public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
						for (String wrapped_struct_type : wrapped_struct_types) {
							desc = desc.replace(wrapped_struct_type, wrapped_struct_flag);
						}

						if (struct2info.containsKey(fqcn) && _methodName.equals(RENAMED_CONSTRUCTOR_NAME)) {
							// remove Object.super() from any Struct constructor
							if (opcode == INVOKESPECIAL && name.equals("<init>") && desc.equals("()V")) {
								super.visitInsn(POP);
								return;
							}
						}

						if (struct2info.containsKey(owner)) {
							if (opcode == INVOKESPECIAL) {
								if (name.equals("<init>")) {
									name = RENAMED_CONSTRUCTOR_NAME;

									// add 'this' as first parameter
									opcode = INVOKESTATIC;
									desc = "(" + wrapped_struct_flag + desc.substring(1);
								}
							} else if (opcode == INVOKEVIRTUAL) {
								// add 'this' as first parameter
								opcode = INVOKESTATIC;
								desc = "(" + wrapped_struct_flag + desc.substring(1);
							}
						}

						if (owner.equals(StructEnv.jvmClassName(Struct.class))) {
							if (name.equals("nullStruct") && desc.equals("(Ljava/lang/Class;)Ljava/lang/Object;")) {
								flow.stack.cas(0, VarType.STRUCT_TYPE, VarType.INT);
								// ..., sizeof
								super.visitInsn(Opcodes.POP);
								// ...
								super.visitInsn(Opcodes.LCONST_0);
								// ..., 'null'
								flow.stack.popEQ(VarType.MISC);
								flow.stack.popEQ(VarType.MISC);
								flow.stack.push(VarType.STRUCT_HI);
								flow.stack.push(VarType.STRUCT_LO);
								return;
							} else if (name.equals("sizeof") && desc.equals("(Ljava/lang/Class;)I")) {
								flow.stack.cas(0, VarType.STRUCT_TYPE, VarType.INT);
								// ...,sizeof
								return;
							} else if (name.equals("nullArray") && desc.equals("(Ljava/lang/Class;I)[Ljava/lang/Object;")) {
								flow.stack.cas(1, VarType.STRUCT_TYPE, VarType.INT);
								// ...,sizeof,length
								flow.visitInsn(Opcodes.SWAP);
								// ...,length,sizeof
								flow.visitInsn(Opcodes.POP);
								// ...,length
								owner = StructEnv.jvmClassName(StructMemory.class);
								name = "nullArray";
								desc = "(I)[" + wrapped_struct_flag;
							} else if (name.equals("malloc") && desc.equals("(Ljava/lang/Class;)Ljava/lang/Object;")) {
								flow.stack.cas(0, VarType.STRUCT_TYPE, VarType.INT);
								// ...,sizeof
								flow.visitIntInsn(BIPUSH, StructMemory.DEFAULT_ALIGNMENT);
								// ...,sizeof,alignment
								owner = StructEnv.jvmClassName(StructGC.class);
								name = "malloc";
								desc = "(II)" + wrapped_struct_flag;
							} else if (name.equals("malloc") && desc.equals("(Ljava/lang/Class;I)Ljava/lang/Object;")) {
								flow.stack.cas(1, VarType.STRUCT_TYPE, VarType.INT);
								// ...,sizeof,alignment
								owner = StructEnv.jvmClassName(StructGC.class);
								name = "malloc";
								desc = "(II)" + wrapped_struct_flag;
							} else if (name.equals("mallocArray") && desc.equals("(Ljava/lang/Class;I)[Ljava/lang/Object;")) {
								flow.stack.cas(1, VarType.STRUCT_TYPE, VarType.INT);
								// ...,sizeof,length
								flow.visitIntInsn(BIPUSH, StructMemory.DEFAULT_ALIGNMENT);
								// ...,sizeof,length,alignment
								owner = StructEnv.jvmClassName(StructGC.class);
								name = "mallocArray";
								desc = "(III)[" + wrapped_struct_flag;
							} else if (name.equals("mallocArray") && desc.equals("(Ljava/lang/Class;II)[Ljava/lang/Object;")) {
								flow.stack.cas(2, VarType.STRUCT_TYPE, VarType.INT);
								// ...,sizeof,length,alignment
								owner = StructEnv.jvmClassName(StructGC.class);
								name = "mallocArray";
								desc = "(III)[" + wrapped_struct_flag;
							} else if (name.equals("calloc") && desc.equals("(Ljava/lang/Class;)Ljava/lang/Object;")) {
								flow.stack.cas(0, VarType.STRUCT_TYPE, VarType.INT);
								// ...,sizeof
								flow.visitIntInsn(BIPUSH, StructMemory.DEFAULT_ALIGNMENT);
								// ...,sizeof,alignment
								owner = StructEnv.jvmClassName(StructGC.class);
								name = "calloc";
								desc = "(II)" + wrapped_struct_flag;
							} else if (name.equals("calloc") && desc.equals("(Ljava/lang/Class;I)Ljava/lang/Object;")) {
								flow.stack.cas(1, VarType.STRUCT_TYPE, VarType.INT);
								// ...,sizeof,alignment
								owner = StructEnv.jvmClassName(StructGC.class);
								name = "calloc";
								desc = "(II)" + wrapped_struct_flag;
							} else if (name.equals("callocArray") && desc.equals("(Ljava/lang/Class;I)[Ljava/lang/Object;")) {
								flow.stack.cas(1, VarType.STRUCT_TYPE, VarType.INT);
								// ...,sizeof,length
								flow.visitIntInsn(BIPUSH, StructMemory.DEFAULT_ALIGNMENT);
								// ...,sizeof,length,alignment
								owner = StructEnv.jvmClassName(StructGC.class);
								name = "callocArray";
								desc = "(III)[" + wrapped_struct_flag;
							} else if (name.equals("callocArray") && desc.equals("(Ljava/lang/Class;II)[Ljava/lang/Object;")) {
								flow.stack.cas(2, VarType.STRUCT_TYPE, VarType.INT);
								// ...,sizeof,length,alignment
								owner = StructEnv.jvmClassName(StructGC.class);
								name = "callocArray";
								desc = "(III)[" + wrapped_struct_flag;
							} else if (name.equals("reallocArray") && desc.equals("(Ljava/lang/Class;[Ljava/lang/Object;I)[Ljava/lang/Object;")) {
								flow.stack.cas(2, VarType.STRUCT_TYPE, VarType.INT);
								// ...,sizeof,struct[],length
								flow.visitIntInsn(BIPUSH, StructMemory.DEFAULT_ALIGNMENT);
								// ...,sizeof,struct[],length,alignment
								owner = StructEnv.jvmClassName(StructGC.class);
								name = "reallocArray";
								desc = "(I[" + wrapped_struct_flag + "II)[" + wrapped_struct_flag;
							} else if (name.equals("reallocArray") && desc.equals("(Ljava/lang/Class;[Ljava/lang/Object;II)[Ljava/lang/Object;")) {
								flow.stack.cas(3, VarType.STRUCT_TYPE, VarType.INT);
								// ...,sizeof,struct[],length,alignment
								owner = StructEnv.jvmClassName(StructGC.class);
								name = "reallocArray";
								desc = "(I[" + wrapped_struct_flag + "II)[" + wrapped_struct_flag;
							} else if (name.equals("mallocArrayBase") && desc.equals("(Ljava/lang/Class;I)Ljava/lang/Object;")) {
								flow.stack.cas(1, VarType.STRUCT_TYPE, VarType.INT);
								// ...,sizeof,length
								flow.visitIntInsn(BIPUSH, StructMemory.DEFAULT_ALIGNMENT);
								// ...,sizeof,length,alignment
								owner = StructEnv.jvmClassName(StructGC.class);
								name = "mallocArrayBase";
								desc = "(III)" + wrapped_struct_flag;
							} else if (name.equals("mallocArrayBase") && desc.equals("(Ljava/lang/Class;II)Ljava/lang/Object;")) {
								flow.stack.cas(2, VarType.STRUCT_TYPE, VarType.INT);
								// ...,sizeof,length,alignment
								owner = StructEnv.jvmClassName(StructGC.class);
								name = "mallocArrayBase";
								desc = "(III)" + wrapped_struct_flag;
							} else if (name.equals("callocArrayBase") && desc.equals("(Ljava/lang/Class;I)Ljava/lang/Object;")) {
								flow.stack.cas(1, VarType.STRUCT_TYPE, VarType.INT);
								// ...,sizeof,length
								flow.visitIntInsn(BIPUSH, StructMemory.DEFAULT_ALIGNMENT);
								// ...,sizeof,length,alignment
								owner = StructEnv.jvmClassName(StructGC.class);
								name = "callocArrayBase";
								desc = "(III)" + wrapped_struct_flag;
							} else if (name.equals("callocArrayBase") && desc.equals("(Ljava/lang/Class;II)Ljava/lang/Object;")) {
								flow.stack.cas(2, VarType.STRUCT_TYPE, VarType.INT);
								// ...,sizeof,length,alignment
								owner = StructEnv.jvmClassName(StructGC.class);
								name = "callocArrayBase";
								desc = "(III)" + wrapped_struct_flag;
							} else if (name.equals("free") && desc.equals("(Ljava/lang/Object;)V")) {
								if (flow.stack.peek() == VarType.NULL_REFERENCE) {
									// ..., NULL
									super.visitInsn(Opcodes.POP); // right thing
									// to do?
									// ...
									return;
								} else if (flow.stack.peek() == VarType.STRUCT_LO) {
									owner = StructEnv.jvmClassName(StructGC.class);
									name = "freeHandle";
									desc = "(" + wrapped_struct_flag + ")V";
								} else {
									throw new IllegalStateException("peek: " + flow.stack.peek());
								}
							} else if (name.equals("free") && desc.equals("([Ljava/lang/Object;)V")) {
								if (flow.stack.peek() == VarType.NULL_REFERENCE) {
									// ..., NULL
									super.visitInsn(Opcodes.POP); // right thing
									// to do?
									// ...
									return;
								} else if (flow.stack.peek() == VarType.STRUCT_ARRAY) {
									owner = StructEnv.jvmClassName(StructGC.class);
									name = "freeHandles";
									desc = "([" + wrapped_struct_flag + ")V";
								} else {
									throw new IllegalStateException("peek: " + flow.stack.peek());
								}
							} else if (name.equals("copy") && desc.equals("(Ljava/lang/Class;Ljava/lang/Object;Ljava/lang/Object;)V")) {
								flow.stack.cas(4, VarType.STRUCT_TYPE, VarType.INT);
								// ...,sizeof,src,dst
								owner = StructEnv.jvmClassName(StructMemory.class);
								name = "copy";
								desc = "(I" + wrapped_struct_flag + "" + wrapped_struct_flag + ")V";
							} else if (name.equals("copy") && desc.equals("(Ljava/lang/Class;Ljava/lang/Object;Ljava/lang/Object;I)V")) {
								flow.stack.cas(5, VarType.STRUCT_TYPE, VarType.INT);
								// ...,sizeof,src,dst,count
								owner = StructEnv.jvmClassName(StructMemory.class);
								name = "copy";
								desc = "(I" + wrapped_struct_flag + "" + wrapped_struct_flag + "I)V";
							} else if (name.equals("swap") && desc.equals("(Ljava/lang/Class;Ljava/lang/Object;Ljava/lang/Object;)V")) {
								flow.stack.cas(4, VarType.STRUCT_TYPE, VarType.INT);
								// ...,sizeof,src,dst
								owner = StructEnv.jvmClassName(StructMemory.class);
								name = "swap";
								desc = "(I" + wrapped_struct_flag + "" + wrapped_struct_flag + ")V";
							} else if (name.equals("view") && desc.equals("(Ljava/lang/Object;Ljava/lang/Class;I)Ljava/lang/Object;")) {
								flow.stack.cas(1, VarType.STRUCT_TYPE, VarType.INT);
								// ...,this,asType,offset
								super.visitInsn(Opcodes.SWAP);
								// ...,this,offset,asType
								super.visitInsn(Opcodes.POP);
								// ...,this,offset
								owner = StructEnv.jvmClassName(StructMemory.class);
								name = "view";
								desc = "(" + wrapped_struct_flag + "I)" + wrapped_struct_flag;
							} else if (name.equals("index") && desc.equals("(Ljava/lang/Object;Ljava/lang/Class;I)Ljava/lang/Object;")) {
								flow.stack.cas(1, VarType.STRUCT_TYPE, VarType.INT);
								// ...,address,sizeof,index
								owner = StructEnv.jvmClassName(StructMemory.class);
								name = "index";
								desc = "(" + wrapped_struct_flag + "II)" + wrapped_struct_flag;
							} else if (name.equals("fromPointer") && desc.equals("(J)Ljava/lang/Object;")) {
								// ..., address
								flow.stack.popEQ(VarType.MISC);
								flow.stack.popEQ(VarType.MISC);
								// ...
								flow.stack.push(VarType.STRUCT_HI);
								flow.stack.push(VarType.STRUCT_LO);
								// ..., pointer
								return;
							} else if (name.equals("fromPointer") && desc.equals("(JLjava/lang/Class;I)[Ljava/lang/Object;")) {
								flow.stack.cas(1, VarType.STRUCT_TYPE, VarType.INT);
								// ...,address,sizeof,length
								owner = StructEnv.jvmClassName(StructMemory.class);
								name = "createPointerArray";
								desc = "(JII)" + array_wrapped_struct_flag;
							} else if (name.equals("fromPointer") && desc.equals("(JII)[Ljava/lang/Object;")) {
								// ...,address,stride,length
								owner = StructEnv.jvmClassName(StructMemory.class);
								name = "createPointerArray";
								desc = "(JII)" + array_wrapped_struct_flag;
							} else if (name.equals("getPointer") && desc.equals("(Ljava/lang/Object;)J")) {
								if (flow.stack.peek() == VarType.NULL_REFERENCE) {
									// ..., NULL
									super.visitInsn(Opcodes.POP);
									// ...
									super.visitInsn(Opcodes.LCONST_0);
									// ..., 0L
									return;
								} else if (flow.stack.peek() == VarType.STRUCT_LO) {
									// ..., pointer
									flow.stack.popEQ(VarType.STRUCT_LO);
									flow.stack.popEQ(VarType.STRUCT_HI);
									// ...
									flow.stack.push(VarType.MISC);
									flow.stack.push(VarType.MISC);
									// ..., address
									return;
								} else {
									throw new IllegalStateException("peek: " + flow.stack.peek());
								}
							} else if (name.equals("isReachable") && desc.equals("(Ljava/lang/Object;)Z")) {
								if (flow.stack.peek() == VarType.NULL_REFERENCE) {
									// ..., NULL
									super.visitInsn(Opcodes.POP);
									// ...
									super.visitInsn(Opcodes.ICONST_0);
									// ..., 'false'
									return;
								} else if (flow.stack.peek() == VarType.STRUCT_LO) {
									owner = StructEnv.jvmClassName(StructMemory.class);
									name = "isValid";
									desc = "(" + wrapped_struct_flag + ")Z";
								} else {
									throw new IllegalStateException("peek: " + flow.stack.peek());
								}
							} else if (name.equals("map") && desc.equals("(Ljava/lang/Class;Ljava/nio/ByteBuffer;)[Ljava/lang/Object;")) {
								// ...,type,buffer
								flow.stack.cas(1, VarType.STRUCT_TYPE, VarType.INT);
								// ...,sizeof,buffer
								owner = StructEnv.jvmClassName(StructMemory.class);
								name = "mapBuffer";
								desc = "(ILjava/nio/ByteBuffer;)[" + wrapped_struct_flag;
							} else if (name.equals("map") && desc.equals("(Ljava/lang/Class;Ljava/nio/ByteBuffer;II)[Ljava/lang/Object;")) {
								// ...,type,buffer,stride,offset
								flow.stack.cas(3, VarType.STRUCT_TYPE, VarType.INT);
								// ...,sizeof,buffer,stride,offset
								owner = StructEnv.jvmClassName(StructMemory.class);
								name = "mapBuffer";
								desc = "(ILjava/nio/ByteBuffer;II)[" + wrapped_struct_flag;
							} else if (name.equals("createStructAllocationStack") && desc.equals("(I)L" + jvmClassName(StructAllocationStack.class) + ";")) {
								owner = StructEnv.jvmClassName(StructMemory.class);
							} else if (name.equals("discardStructAllocationStack") && desc.equals("(L" + jvmClassName(StructAllocationStack.class) + ";)V")) {
								owner = StructEnv.jvmClassName(StructMemory.class);
							} else if (name.equals("stackAlloc") && desc.equals("(L" + jvmClassName(StructAllocationStack.class) + ";Ljava/lang/Class;)Ljava/lang/Object;")) {
								flow.stack.cas(0, VarType.STRUCT_TYPE, VarType.INT);
								// ...,stack,sizeof
								flow.visitIntInsn(Opcodes.BIPUSH, 4);
								// ...,stack,sizeof,alignment
								owner = StructEnv.jvmClassName(StructMemory.class);
								name = "malloc";
								desc = "(L" + jvmClassName(StructAllocationStack.class) + ";II)" + wrapped_struct_flag;
							}
						}

						super.visitMethodInsn(opcode, owner, name, desc, itf);
					}

					@Override
					public void visitLdcInsn(Object cst) {
						if (cst instanceof Type) {
							if (plain_struct_types.contains(((Type) cst).getInternalName())) {
								flow.visitIntInsn(Opcodes.SIPUSH, struct2info.get(((Type) cst).getInternalName()).sizeof);
								flow.stack.popEQ(VarType.INT);
								flow.stack.push(VarType.STRUCT_TYPE);
								return;
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

		if (StructEnv.PRINT_LOG) {
			System.out.println("StructEnv.rewrite TRANSFORM [" + fqcn + "]:");
		}

		try {
			new ClassReader(bytecode).accept(visitor, 0);
		} catch (Throwable cause) {
			String msg = "LibStruct failed to rewrite classpath:\n";
			msg += "\tLast entered class: \n";
			msg += "\t\tfqcn: " + fqcn + "\n";
			msg += "\n";
			msg += "\tLast entered method: \n";
			msg += "\t\tname: " + currentMethodName[0] + "\n";
			msg += "\t\tdesc: " + currentMethodDesc[0] + "\n";
			if (!StructEnv.PRINT_LOG)
				msg += "\n\t\tfor more information set: -DLibStruct.PRINT_LOG=true";
			throw new IllegalStateException(msg, cause);
		}

		for (Entry<String, Set<String>> entry : final2origMethods.entrySet()) {
			if (entry.getValue().size() > 1) {
				String msg = "LibStruct failed to rewrite classpath:\n";
				msg += "\tLast entered class: \n";
				msg += "\t\tfqcn: " + fqcn + "\n";
				msg += "\n";
				msg += "\tThe following methods collide after transformation: \n";
				for (String m : entry.getValue())
					msg += "\t\t" + m + "\n";
				msg += "\tdue to shared name and description: (struct references are rewritten to longs)\n";
				msg += "\t\t" + entry.getKey() + "\n";
				if (!StructEnv.PRINT_LOG)
					msg += "\n\t\tfor more information set: -DLibStruct.PRINT_LOG=true";
				throw new IllegalStateException(msg);
			}
		}

		bytecode = writer.toByteArray();

		if (StructEnv.PRINT_LOG) {
			System.out.println("StructEnv.rewrite OUTPUT [" + fqcn + "]:");
			int flags = 0;
			ClassReader cr = new ClassReader(bytecode);
			cr.accept(new TraceClassVisitor(null, new Textifier(), new PrintWriter(System.out)), flags);
		}

		return bytecode;
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