package net.indiespot.struct.transform;

import static org.objectweb.asm.Opcodes.*;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.indiespot.struct.StructInfo;
import net.indiespot.struct.cp.CopyStruct;
import net.indiespot.struct.cp.StructType;
import net.indiespot.struct.cp.TakeStruct;
import net.indiespot.struct.runtime.StructAllocationStack;
import net.indiespot.struct.runtime.StructMemory;
import net.indiespot.struct.runtime.StructThreadLocalStack;
import net.indiespot.struct.runtime.StructUtil;

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

public class StructEnv {
	public static final boolean print_log = true;
	public static final String plain_struct_flag = "$truct";
	public static final String wrapped_struct_flag = "L" + plain_struct_flag + ";";
	public static final String array_wrapped_struct_flag = "[L" + plain_struct_flag + ";";
	private static final String RENAMED_CONSTRUCTOR_NAME = "_<init>_";

	private static Set<String> plain_struct_types = new HashSet<>();
	private static Set<String> wrapped_struct_types = new HashSet<>();
	private static Set<String> array_wrapped_struct_types = new HashSet<>();
	private static Map<String, StructInfo> struct2info = new HashMap<>();

	private static final boolean struct_rewrite_early_out = true;
	private static volatile boolean is_rewriting = false;

	public static void addStruct(StructInfo info) {
		info.validate();
		if(is_rewriting)
			throw new IllegalStateException("cannot add struct definition when classes have been rewritten already");
		struct2info.put(info.fqcn, info);
		plain_struct_types.add(info.fqcn);
		wrapped_struct_types.add("L" + info.fqcn + ";");
		array_wrapped_struct_types.add("[L" + info.fqcn + ";");
	}

	private static enum ReturnValueStrategy {
		COPY, PASS
	}

	private static class ClassInfo {
		final Set<String> fieldsWithStructType = new HashSet<>();
		final Set<String> methodsWithStructAccess = new HashSet<>();
		final Set<String> methodsWithStructCreation = new HashSet<>();
		final Map<String, Integer> methodNameDesc2locals = new HashMap<>();

		public boolean needsRewrite() {
			return !fieldsWithStructType.isEmpty() || !methodsWithStructAccess.isEmpty() || !methodsWithStructCreation.isEmpty();
		}

		private void analyze(final String fqcn, byte[] bytecode) {

			if(print_log)
				System.out.println("analyzing class: " + fqcn);

			ClassWriter writer = new ClassWriter(0);
			ClassVisitor visitor = new ClassVisitor(Opcodes.ASM4, writer) {

				@Override
				public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
					if(desc.equals("L" + StructEnv.jvmClassName(StructType.class) + ";")) {
						if(!plain_struct_types.contains(fqcn)) {
							throw new IllegalStateException("encountered undefined struct: " + fqcn);
						}
					}
					return super.visitAnnotation(desc, visible);
				}

				@Override
				public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
					if(wrapped_struct_types.contains(desc)) {
						this.flagRewriteField(name, desc);
					}
					if(array_wrapped_struct_types.contains(desc)) {
						this.flagRewriteField(name, desc);
					}

					return super.visitField(access, name, desc, signature, value);
				}

				private void flagRewriteField(String name, String desc) {
					if(fieldsWithStructType.add(name + desc))
						if(print_log)
							System.out.println("\tflagging field: " + name + "" + desc);
				}

				@Override
				public MethodVisitor visitMethod(int access, final String methodName, final String methodDesc, String signature, String[] exceptions) {
					return new MethodVisitor(Opcodes.ASM4, super.visitMethod(access, methodName, methodDesc, signature, exceptions)) {
						{
							if(print_log)
								System.out.println("\tchecking method for rewrite: " + methodName + "" + methodDesc);

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
							if(owner.equals(jvmClassName(StructUtil.class))) {
								this.flagRewriteMethod(false);
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

						private void flagRewriteMethod(boolean forStructCreation) {
							if(methodsWithStructAccess.add(methodName + methodDesc))
								if(print_log)
									System.out.println("\t\tflagged for rewrite");

							if(forStructCreation) {
								methodsWithStructCreation.add(methodName + methodDesc);
							}
						}

						@Override
						public void visitMaxs(int maxStack, int maxLocals) {
							methodNameDesc2locals.put(methodName + methodDesc, maxLocals);
							super.visitMaxs(maxStack, maxLocals);
						}
					};
				}
			};
			new ClassReader(bytecode).accept(visitor, 0);
		}
	}

	public static byte[] rewriteClass(final String fqcn, byte[] bytecode) {
		is_rewriting = true;

		final ClassInfo info = new ClassInfo();
		info.analyze(fqcn, bytecode);
		if(!info.needsRewrite())
			return null;

		final ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
			protected String getCommonSuperClass(String type1, String type2) {
				String type3 = super.getCommonSuperClass(type1, type2);
				System.out.println("StructEnv.rewriteClass ClassWriter.getCommonSuperClass(" + type1 + ", " + type2 + ") -> " + type3);
				return type3;
			}
		};

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
				if(array_wrapped_struct_types.contains(desc))
					desc = array_wrapped_struct_flag;
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
					boolean hasStructAccess = info.methodsWithStructAccess.contains(origMethodName + origMethodDesc);
					if(print_log)
						System.out.println("\t\tearly out for rewrite? [" + !hasStructAccess + "]");
					if(!hasStructAccess)
						return mv; // nope!
				}
				final boolean hasStructCreation = info.methodsWithStructCreation.contains(origMethodName + origMethodDesc);

				final String _methodName = methodName;

				final FlowAnalysisMethodVisitor flow = new FlowAnalysisMethodVisitor(mv, access, fqcn, methodName, methodDesc, signature, exceptions);
				return new MethodVisitor(Opcodes.ASM4, flow) {
					private ReturnValueStrategy strategy;

					@Override
					public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
						if(desc.equals("L" + jvmClassName(CopyStruct.class) + ";")) {
							strategy = ReturnValueStrategy.COPY;
							if(print_log)
								System.out.println("\t\t\twith struct return value, with Copy strategy");
						}
						else if(desc.equals("L" + jvmClassName(TakeStruct.class) + ";")) {
							strategy = ReturnValueStrategy.PASS;
							if(print_log)
								System.out.println("\t\t\twith struct return value, with Pass strategy");
						}
						return super.visitAnnotation(desc, visible);
					}

					@Override
					public void visitCode() {
						if(_returnsStructType != null) {
							String msg = "";
							msg += "must define how struct return values are handled: ";
							msg += "\n\t\t" + fqcn + "." + _methodName + origMethodDesc;

							if(strategy == null)
								throw new IllegalStateException(msg);
						}

						super.visitCode();

						if(hasStructCreation) {
							// ...
							super.visitMethodInsn(INVOKESTATIC, jvmClassName(StructThreadLocalStack.class), "saveStack", "()L" + StructAllocationStack.class.getName().replace('.', '/') + ";");
							// ..., sas
							super.visitVarInsn(ASTORE, info.methodNameDesc2locals.get(origMethodName + origMethodDesc).intValue());
						}
					}

					@Override
					public void visitInsn(int opcode) {
						if(hasStructCreation) {
							switch (opcode) {
							case RETURN:
							case ARETURN:
							case IRETURN:
							case FRETURN:
							case LRETURN:
							case DRETURN:
								super.visitVarInsn(ALOAD, info.methodNameDesc2locals.get(origMethodName + origMethodDesc).intValue());
								super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, StructEnv.jvmClassName(StructAllocationStack.class), "restore", "()V");
								break;
							}
						}

						if(opcode == ARETURN && flow.stack.peek() == VarType.STRUCT) {
							if(strategy == null)
								throw new IllegalStateException();

							if(print_log)
								System.out.println("\t\t\treturn value strategy: " + strategy);

							if(strategy == ReturnValueStrategy.PASS) {
								// no-op
							}
							else if(strategy == ReturnValueStrategy.COPY) {
								super.visitIntInsn(BIPUSH, struct2info.get(_returnsStructType).sizeof);
								super.visitMethodInsn(INVOKESTATIC, StructEnv.jvmClassName(StructMemory.class), "allocateCopy", "(" + wrapped_struct_flag + "I)" + wrapped_struct_flag);
							}
							else {
								throw new IllegalStateException();
							}
						}

						super.visitInsn(opcode);
					}

					@Override
					public void visitFrame(int type, int nLocal, Object[] local, int nStack, Object[] stack) {
						if(local != null) {
							for(int i = 0; i < local.length; i++) {
								if(array_wrapped_struct_types.contains(local[i]))
									local[i] = array_wrapped_struct_flag;
								else if(wrapped_struct_types.contains(local[i]))
									local[i] = wrapped_struct_flag;
							}
						}
						if(stack != null) {
							for(int i = 0; i < stack.length; i++) {
								if(array_wrapped_struct_types.contains(stack[i]))
									stack[i] = array_wrapped_struct_flag;
								else if(wrapped_struct_types.contains(stack[i]))
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
								// super.visitMethodInsn(Opcodes.INVOKESTATIC,
								// StructBuild.jvmClassName(StructMemory.class),
								// "allocate", "(I)" + wrapped_struct_flag);

								super.visitVarInsn(ALOAD, info.methodNameDesc2locals.get(origMethodName + origMethodDesc).intValue());
								if(struct2info.get(type).skipZeroFill)
									super.visitMethodInsn(Opcodes.INVOKESTATIC, StructEnv.jvmClassName(StructMemory.class), "allocateSkipZeroFill", "(IL" + StructAllocationStack.class.getName().replace('.', '/') + ";)" + wrapped_struct_flag);
								else
									super.visitMethodInsn(Opcodes.INVOKESTATIC, StructEnv.jvmClassName(StructMemory.class), "allocate", "(IL" + StructAllocationStack.class.getName().replace('.', '/') + ";)" + wrapped_struct_flag);
								return;
							}
						}
						else if(opcode == ANEWARRAY) {
							if(struct2info.containsKey(type)) {
								super.visitIntInsn(Opcodes.BIPUSH, struct2info.get(type).sizeof);
								super.visitMethodInsn(Opcodes.INVOKESTATIC, StructEnv.jvmClassName(StructMemory.class), "allocateArray", "(II)" + array_wrapped_struct_flag);
								flow.stack.popEQ(VarType.STRUCT_ARRAY);
								flow.stack.push(VarType.STRUCT_ARRAY);
								return;
							}
						}
						else if(opcode == CHECKCAST) {
							if(flow.stack.peek() == VarType.STRUCT) {
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
								super.visitMethodInsn(INVOKESTATIC, StructEnv.jvmClassName(StructMemory.class), type.toLowerCase() + "put", "(" + wrapped_struct_flag + type + "I)V");
								return;
							}

						}
						else if(opcode == GETFIELD) {
							if(struct2info.containsKey(owner)) {
								StructInfo info = struct2info.get(owner);
								int offset = info.field2offset.get(name).intValue();
								String type = info.field2type.get(name);
								super.visitIntInsn(BIPUSH, offset);
								super.visitMethodInsn(INVOKESTATIC, StructEnv.jvmClassName(StructMemory.class), type.toLowerCase() + "get", "(" + wrapped_struct_flag + "I)" + type);
								return;
							}
						}

						if(wrapped_struct_types.contains(desc)) {
							desc = wrapped_struct_flag;
						}
						if(array_wrapped_struct_types.contains(desc)) {
							desc = array_wrapped_struct_flag;
						}

						super.visitFieldInsn(opcode, owner, name, desc);
					}

					@Override
					public void visitMethodInsn(int opcode, String owner, String name, String desc) {
						for(String structType : struct2info.keySet()) {
							desc = desc.replace(structType, plain_struct_flag); // FIXME?
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

						if(owner.equals(StructEnv.jvmClassName(StructUtil.class))) {
							if(name.equals("getPointer") && desc.equals("(Ljava/lang/Object;)J")) {
								if(flow.stack.peek() == VarType.NULL) {
									// ..., NULL
									super.visitInsn(Opcodes.POP);
									// ...
									super.visitInsn(Opcodes.ICONST_M1);
									// ..., -1
									super.visitInsn(Opcodes.I2L);
									// ..., -1L
									return;
								}
								else if(flow.stack.peek() == VarType.STRUCT) {
									owner = StructEnv.jvmClassName(StructMemory.class);
									name = "handle2pointer";
									desc = "(" + wrapped_struct_flag + ")J";
								}
								else {
									throw new IllegalStateException("peek: " + flow.stack.peek());
								}
							}
							else if(name.equals("isReachable") && desc.equals("(Ljava/lang/Object;)Z")) {
								if(flow.stack.peek() == VarType.NULL) {
									// ..., NULL
									super.visitInsn(Opcodes.POP);
									// ...
									super.visitInsn(Opcodes.ICONST_0);
									// ..., 'false'
									return;
								}
								else if(flow.stack.peek() == VarType.STRUCT) {
									owner = StructEnv.jvmClassName(StructMemory.class);
									name = "isValid";
									desc = "(" + wrapped_struct_flag + ")Z";
								}
								else {
									throw new IllegalStateException("peek: " + flow.stack.peek());
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
										owner = StructEnv.jvmClassName(StructMemory.class);
										name = "mapArray";
										desc = "(Ljava/nio/ByteBuffer;I)[" + wrapped_struct_flag;
									}
								}
								else {
									throw new IllegalStateException();
								}
							}
						}

						super.visitMethodInsn(opcode, owner, name, desc);
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