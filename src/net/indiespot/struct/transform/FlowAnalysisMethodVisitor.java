package net.indiespot.struct.transform;

import static org.objectweb.asm.Opcodes.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import net.indiespot.struct.codegen.StructGenericsSourcecodeGenerator;
import net.indiespot.struct.runtime.StructMemory;
import net.indiespot.struct.runtime.UnsupportedCallsiteException;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class FlowAnalysisMethodVisitor extends MethodVisitor {
	public VarStack stack;
	public VarLocal local;
	private int maxLocals;

	private final String callsiteDescription;
	private final int expandedParamSizeof;

	public FlowAnalysisMethodVisitor(int usedLocalvarSlots, MethodVisitor mv, int access, String owner, String name, String desc, String signature, String[] exceptions) {
		super(Opcodes.ASM5, mv);

		stack = new VarStack(8);
		local = new VarLocal(usedLocalvarSlots);
		maxLocals = usedLocalvarSlots + (usedLocalvarSlots << 1);

		callsiteDescription = owner + "." + name + "" + desc;

		String params = desc.substring(desc.indexOf('(') + 1, desc.indexOf(')'));
		int offset = 0;
		if ((access & Opcodes.ACC_STATIC) == 0) {
			local.set(offset++, VarType.REFERENCE); // 'this'
		}

		for (String paramDesc : splitDescArray(params)) {
			offset = setLocalvarFromParam(local, offset, descToType(paramDesc));
		}

		expandedParamSizeof = local.setupParamsRemapTable(offset, usedLocalvarSlots);
		if (StructEnv.PRINT_LOG)
			System.out.println("offset=" + offset);
		if (StructEnv.PRINT_LOG)
			System.out.println("expandedParamSizeof=" + expandedParamSizeof);
	}

	@Override
	public AnnotationVisitor visitAnnotationDefault() {
		return super.visitAnnotationDefault();
	}

	@Override
	public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
		return super.visitAnnotation(desc, visible);
	}

	@Override
	public AnnotationVisitor visitParameterAnnotation(int parameter, String desc, boolean visible) {
		return super.visitParameterAnnotation(parameter, desc, visible);
	}

	@Override
	public void visitAttribute(Attribute attr) {
		super.visitAttribute(attr);
	}

	@Override
	public void visitCode() {
		super.visitCode();
	}

	@Override
	public void visitFrame(int type, int nLocal, Object[] local, int nStack, Object[] stack) {
		// System.out.println("Flow.visitFrame: local=" + Arrays.toString(local)
		// + ", stack=" + Arrays.toString(stack));

		super.visitFrame(type, nLocal, local, nStack, stack);
	}

	@Override
	public void visitInsn(int opcode) {
		if (StructEnv.PRINT_LOG)
			System.out.println("\t1)\t" + opcodeToString(opcode));

		switch (opcode) {
		case ACONST_NULL:
			stack.push(VarType.NULL);
			break;

		case ICONST_M1:
		case ICONST_0:
		case ICONST_1:
		case ICONST_2:
		case ICONST_3:
		case ICONST_4:
		case ICONST_5:
			stack.push(VarType.INT);
			break;

		case FCONST_0:
		case FCONST_1:
		case FCONST_2:
			stack.push(VarType.MISC);
			break;

		case LCONST_0:
		case LCONST_1:
		case DCONST_0:
		case DCONST_1:
			stack.push(VarType.MISC);
			stack.push(VarType.MISC);
			break;

		case BALOAD:
		case CALOAD:
		case SALOAD:
			stack.popEQ(VarType.INT); // index
			stack.popEQ(VarType.REFERENCE); // array
			stack.push(VarType.INT); // value
			break;

		case IALOAD:
			stack.popEQ(VarType.INT); // index
			stack.popEQ(VarType.REFERENCE); // array
			stack.push(VarType.INT); // value
			break;

		case FALOAD:
			stack.popEQ(VarType.INT); // index
			stack.popEQ(VarType.REFERENCE); // array
			stack.push(VarType.MISC); // value
			break;

		case LALOAD:
		case DALOAD:
			stack.popEQ(VarType.INT); // index
			stack.popEQ(VarType.REFERENCE); // array
			stack.push(VarType.MISC); // value
			stack.push(VarType.MISC); // value
			break;

		case AALOAD:
			stack.popEQ(VarType.INT); // index
			switch (stack.pop()) { // array
			case REFERENCE:
				stack.push(VarType.REFERENCE); // value
				break;
			case STRUCT_ARRAY:
				stack.push(VarType.STRUCT_HI); // value
				stack.push(VarType.STRUCT_LO); // value
				opcode = LALOAD;
				break;
			default:
				throw new IllegalStateException();
			}
			break;

		case BASTORE:
		case CASTORE:
		case SASTORE:
			stack.popEQ(VarType.INT); // value
			stack.popEQ(VarType.INT); // index
			stack.popEQ(VarType.REFERENCE); // array
			break;

		case IASTORE:
			stack.popEQ(VarType.INT); // value
			stack.popEQ(VarType.INT); // index
			stack.popEQ(VarType.REFERENCE); // array
			break;

		case FASTORE:
			stack.popEQ(VarType.MISC); // value
			stack.popEQ(VarType.INT); // index
			stack.popEQ(VarType.REFERENCE); // array
			break;

		case LASTORE:
		case DASTORE:
			stack.popEQ(VarType.MISC); // value
			stack.popEQ(VarType.MISC); // value
			stack.popEQ(VarType.INT); // index
			stack.popEQ(VarType.REFERENCE); // array
			break;

		case AASTORE: {
			VarType got = stack.popEQ(EnumSet.of(VarType.REFERENCE, VarType.STRUCT_LO)); // value
			if (got == VarType.STRUCT_LO) {
				stack.popEQ(VarType.STRUCT_HI);
				opcode = LASTORE;
			}
			stack.popEQ(VarType.INT); // index
			if (got == VarType.STRUCT_LO)
				stack.popEQ(VarType.STRUCT_ARRAY); // array
			else
				stack.popEQ(VarType.REFERENCE); // array
			break;
		}

		case POP:
			if (stack.peek() == VarType.STRUCT_LO) {
				stack.pop();
				stack.pop();
				opcode = POP2;
			} else {
				stack.pop();
			}
			break;

		case POP2:
			for (int i = 0; i < 2; i++)
				if (stack.peek(i) == VarType.STRUCT_LO || stack.peek(i) == VarType.STRUCT_HI)
					throw new IllegalStateException();
			stack.pop();
			stack.pop();
			break;

		case DUP:
			if (stack.peek(0) == VarType.STRUCT_LO && //
					stack.peek(1) == VarType.STRUCT_HI) {
				stack.push(stack.peek(1));
				stack.push(stack.peek(1));
				opcode = DUP2;
			} else {
				stack.push(stack.peek());
			}
			break;

		case DUP_X1: {
			for (int i = 0; i < 2; i++)
				if (stack.peek(i) == VarType.STRUCT_LO || stack.peek(i) == VarType.STRUCT_HI)
					throw new IllegalStateException();
			VarType got1 = stack.pop();
			VarType got2 = stack.pop();
			stack.push(got1);
			stack.push(got2);
			stack.push(got1);
			break;
		}

		case DUP_X2: {
			for (int i = 0; i < 3; i++)
				if (stack.peek(i) == VarType.STRUCT_LO || stack.peek(i) == VarType.STRUCT_HI)
					throw new IllegalStateException();
			VarType got1 = stack.pop();
			VarType got2 = stack.pop();
			VarType got3 = stack.pop();

			stack.push(got1);
			stack.push(got3);
			stack.push(got2);
			stack.push(got1);
			break;
		}

		case DUP2: {
			if (stack.peek(0) == VarType.STRUCT_LO && //
					stack.peek(1) == VarType.STRUCT_HI) {
				super.visitInsn(DUP2_X2);
				super.visitInsn(DUP2_X2);
				stack.push(stack.peek(1));
				stack.push(stack.peek(1));
				stack.push(stack.peek(1));
				stack.push(stack.peek(1));
				return;
			} else {
				for (int i = 0; i < 2; i++)
					if (stack.peek(i) == VarType.STRUCT_LO || stack.peek(i) == VarType.STRUCT_HI)
						throw new IllegalStateException();
				VarType got1 = stack.pop();
				VarType got2 = stack.pop();
				stack.push(got2);
				stack.push(got1);
				stack.push(got2);
				stack.push(got1);
			}
			break;
		}

		case DUP2_X1: {
			for (int i = 0; i < 3; i++)
				if (stack.peek(i) == VarType.STRUCT_LO || stack.peek(i) == VarType.STRUCT_HI)
					throw new IllegalStateException();
			VarType got1 = stack.pop();
			VarType got2 = stack.pop();
			VarType got3 = stack.pop();
			stack.push(got2);
			stack.push(got1);
			stack.push(got3);
			stack.push(got2);
			stack.push(got1);
			break;
		}

		case DUP2_X2: {
			for (int i = 0; i < 4; i++)
				if (stack.peek(i) == VarType.STRUCT_LO || stack.peek(i) == VarType.STRUCT_HI)
					throw new IllegalStateException();
			VarType got1 = stack.pop();
			VarType got2 = stack.pop();
			VarType got3 = stack.pop();
			VarType got4 = stack.pop();
			stack.push(got2);
			stack.push(got1);
			stack.push(got4);
			stack.push(got3);
			stack.push(got2);
			stack.push(got1);
			break;
		}

		case IADD:
		case ISUB:
		case IMUL:
		case IDIV:
		case IREM:
		case ISHR:
		case ISHL:
		case IUSHR:
		case IXOR:
		case IAND:
		case IOR:
			stack.popEQ(VarType.INT);
			stack.popEQ(VarType.INT);
			stack.push(VarType.INT);
			break;

		case FMUL:
		case FADD:
		case FREM:
		case FSUB:
		case FDIV:
			stack.popEQ(VarType.MISC);
			stack.popEQ(VarType.MISC);
			stack.push(VarType.MISC);
			break;

		case LADD:
		case DADD:
		case LSUB:
		case DSUB:
		case LMUL:
		case DMUL:
		case LDIV:
		case DDIV:
		case LREM:
		case DREM:
		case LAND:
		case LOR:
		case LXOR:
			stack.popEQ(VarType.MISC);
			stack.popEQ(VarType.MISC);
			stack.popEQ(VarType.MISC);
			stack.popEQ(VarType.MISC);
			stack.push(VarType.MISC);
			stack.push(VarType.MISC);
			break;

		case LUSHR:
		case LSHR:
		case LSHL:
			stack.popEQ(VarType.INT);
			stack.popEQ(VarType.MISC);
			stack.popEQ(VarType.MISC);
			stack.push(VarType.MISC);
			stack.push(VarType.MISC);
			break;

		case I2F:
			stack.popEQ(VarType.INT);
			stack.push(VarType.MISC);
			break;

		case I2B:
		case I2C:
		case I2S:
		case INEG:
			stack.popEQ(VarType.INT);
			stack.push(VarType.INT);
			break;

		case F2I:
			stack.popEQ(VarType.MISC);
			stack.push(VarType.INT);
			break;

		case SWAP: {
			VarType t1 = stack.pop();
			VarType t2 = stack.pop();
			stack.push(t1);
			stack.push(t2);
			break;
		}

		case FNEG:
		case LNEG:
		case DNEG:
		case L2D:
		case D2L:
			stack.popEQ(VarType.MISC);
			stack.push(VarType.MISC);
			break;

		case I2L:
		case I2D:
			stack.popEQ(VarType.INT);
			stack.push(VarType.MISC);
			stack.push(VarType.MISC);
			break;

		case F2L:
		case F2D:
			stack.popEQ(VarType.MISC);
			stack.push(VarType.MISC);
			stack.push(VarType.MISC);
			break;

		case L2I:
		case D2I:
			stack.popEQ(VarType.MISC);
			stack.popEQ(VarType.MISC);
			stack.push(VarType.INT);
			break;

		case L2F:
		case D2F:
			stack.popEQ(VarType.MISC);
			stack.popEQ(VarType.MISC);
			stack.push(VarType.MISC);
			break;

		case LCMP:
		case DCMPL:
		case DCMPG:
			stack.popEQ(VarType.MISC);
			stack.popEQ(VarType.MISC);
			stack.popEQ(VarType.MISC);
			stack.popEQ(VarType.MISC);
			stack.push(VarType.INT);
			break;

		case FCMPL:
		case FCMPG:
			stack.popEQ(VarType.MISC);
			stack.popEQ(VarType.MISC);
			stack.push(VarType.INT);
			break;

		case IRETURN:
			stack.popEQ(VarType.INT);
			stack.eqEmpty();
			stack = null;
			local = null;
			break;

		case FRETURN:
			stack.popEQ(VarType.MISC);
			stack.eqEmpty();
			stack = null;
			local = null;
			break;

		case LRETURN:
		case DRETURN:
			stack.popEQ(VarType.MISC);
			stack.popEQ(VarType.MISC);
			stack.eqEmpty();
			stack = null;
			local = null;
			break;

		case ARETURN: {
			VarType got = stack.popEQ(EnumSet.of(VarType.REFERENCE, VarType.STRUCT_LO, VarType.STRUCT_ARRAY, VarType.NULL));
			if (got == VarType.STRUCT_LO) {
				stack.popEQ(VarType.STRUCT_HI);
				opcode = LRETURN;
			}
			stack.eqEmpty();
			stack = null;
			local = null;
			break;
		}

		case RETURN:
			stack.eqEmpty();
			stack = null;
			local = null;
			break;

		case ARRAYLENGTH:
			stack.popEQ(EnumSet.of(VarType.REFERENCE, VarType.STRUCT_ARRAY));
			stack.push(VarType.INT);
			break;

		case MONITORENTER:
		case MONITOREXIT:
			stack.popEQ(VarType.REFERENCE);
			break;

		case ATHROW:
			stack.popEQ(VarType.REFERENCE);
			break;

		default:
			throw new IllegalStateException("unhandled opcode: " + opcodeToString(opcode));
		}

		if (StructEnv.PRINT_LOG)
			System.out.println("\t2)\t" + opcodeToString(opcode));
		super.visitInsn(opcode);
	}

	@Override
	public void visitIntInsn(int opcode, int operand) {
		if (StructEnv.PRINT_LOG)
			System.out.println("\t\t" + opcodeToString(opcode) + " " + operand);

		switch (opcode) {
		case BIPUSH:
		case SIPUSH:
			stack.push(VarType.INT);
			break;

		case NEWARRAY:
			stack.popEQ(VarType.INT); // length
			stack.push(VarType.REFERENCE);
			break;

		default:
			throw new IllegalStateException("unhandled opcode: " + opcodeToString(opcode));
		}

		super.visitIntInsn(opcode, operand);
	}

	@Override
	public void visitVarInsn(int opcode, int var) {
		if (StructEnv.PRINT_LOG)
			System.out.println("\t1)\t" + opcodeToString(opcode) + " " + var);

		switch (opcode) {
		case ILOAD:
			local.getEQ(var, VarType.INT);
			var = local.remap(var);
			stack.push(VarType.INT);
			break;

		case FLOAD:
			local.getEQ(var, VarType.MISC);
			var = local.remap(var);
			stack.push(VarType.MISC);
			break;

		case LLOAD:
		case DLOAD:
			local.getEQ(var + 0, VarType.MISC);
			local.getEQ(var + 1, VarType.MISC);
			var = local.remap(var);
			stack.push(VarType.MISC);
			stack.push(VarType.MISC);
			break;

		case ALOAD: {
			if (StructEnv.PRINT_LOG)
				System.out.println("\t\t\t" + local.get(var));
			if (local.get(var) == VarType.STRUCT_HI) {
				opcode = LLOAD;
				var = local.remap(var);
				if (local.getUnmapped(var + 0) != VarType.STRUCT_HI)
					throw new IllegalStateException();
				if (local.getUnmapped(var + 1) != VarType.STRUCT_LO)
					throw new IllegalStateException();
				if (StructEnv.PRINT_LOG)
					System.out.println("\t2)\t" + opcodeToString(opcode) + " " + var);
				stack.push(VarType.STRUCT_HI);
				stack.push(VarType.STRUCT_LO);
			} else if (local.get(var) == VarType.STRUCT_LOCALVAR) {
				opcode = LLOAD;
				var = local.getStructBaseIndexMapped(var);
				stack.push(VarType.STRUCT_HI);
				stack.push(VarType.STRUCT_LO);
			} else {
				VarType got = local.getEQ(var, EnumSet.of(VarType.REFERENCE, VarType.STRUCT_ARRAY, VarType.STRUCT_TYPE, VarType.NULL, VarType.EMBEDDED_ARRAY));
				if (got == VarType.STRUCT_TYPE || got == VarType.EMBEDDED_ARRAY) {
					opcode = ILOAD;
					var = local.remap(var);
					stack.push(got);
				} else {
					var = local.remap(var);
					stack.push(got);
				}
			}
			break;
		}

		// ---

		case ISTORE:
			stack.popEQ(VarType.INT);
			var = local.set(var, VarType.INT);
			break;

		case FSTORE:
			stack.popEQ(VarType.MISC);
			var = local.set(var, VarType.MISC);
			break;

		case LSTORE:
		case DSTORE:
			stack.popEQ(VarType.MISC);
			stack.popEQ(VarType.MISC);
			local.set(var + 0, VarType.MISC);
			local.set(var + 1, VarType.MISC);
			var = local.remap(var);
			break;

		case ASTORE: {
			VarType got = stack.popEQ(EnumSet.of(VarType.REFERENCE, VarType.STRUCT_LO, VarType.STRUCT_ARRAY, VarType.STRUCT_TYPE, VarType.NULL, VarType.EMBEDDED_ARRAY));
			if (StructEnv.PRINT_LOG)
				System.out.println("\t\t\t" + got);
			if (got == VarType.STRUCT_LO) {
				stack.popEQ(VarType.STRUCT_HI);
				var = local.set(var, VarType.STRUCT_LOCALVAR);
				opcode = LSTORE;
			} else if (got == VarType.STRUCT_TYPE || got == VarType.EMBEDDED_ARRAY) {
				opcode = ISTORE;
				var = local.set(var, got);
			} else {
				var = local.set(var, got);
			}
			break;
		}

		case RET:
			throw new UnsupportedOperationException();

		default:
			throw new IllegalStateException("unhandled opcode: " + opcodeToString(opcode));
		}

		if (StructEnv.PRINT_LOG)
			System.out.println("\t2)\t" + opcodeToString(opcode) + " " + var);
		super.visitVarInsn(opcode, var);
	}

	@Override
	public void visitTypeInsn(int opcode, String type) {
		if (StructEnv.PRINT_LOG)
			System.out.println("\t\t" + opcodeToString(opcode) + " " + type);

		switch (opcode) {
		case NEW:
			if (type.equals(StructEnv.plain_struct_flag)) {
				stack.push(VarType.STRUCT_HI);
				stack.push(VarType.STRUCT_LO);
			} else {
				stack.push(VarType.REFERENCE);
			}
			break;

		case ANEWARRAY:
			stack.popEQ(VarType.INT); // length
			if (type.equals(StructEnv.plain_struct_flag))
				stack.push(VarType.STRUCT_ARRAY);
			else
				stack.push(VarType.REFERENCE);
			break;

		case CHECKCAST:
			break; // no-op

		case INSTANCEOF: {
			VarType got = stack.popEQ(EnumSet.of(VarType.REFERENCE, VarType.STRUCT_LO, VarType.STRUCT_ARRAY, VarType.NULL));
			if (got == VarType.STRUCT_LO) {
				stack.popEQ(VarType.STRUCT_HI);
				super.visitInsn(POP2); // discard REF
				super.visitInsn(ICONST_0); // false (struct is never instanceof
											// any type)
				stack.push(VarType.INT);
				return;
			}
			stack.push(VarType.INT);
			break;
		}

		default:
			throw new IllegalStateException("unhandled opcode: " + opcodeToString(opcode));
		}

		super.visitTypeInsn(opcode, type);
	}

	@Override
	public void visitFieldInsn(int opcode, String owner, String name, String desc) {
		if (StructEnv.PRINT_LOG)
			System.out.println("\t1)\t" + opcodeToString(opcode) + " " + owner + " " + name + " " + desc);

		if (opcode == GETFIELD || opcode == GETSTATIC) {
			if (opcode == GETFIELD) {
				stack.popEQ(VarType.REFERENCE);
			}

			switch (pushDescType(stack, descToType(desc))) {
			case STRUCT_LO:
				desc = "J";
				break;

			case STRUCT_ARRAY:
				desc = "[J";
				break;

			default:
				break;
			}
		} else if (opcode == PUTSTATIC || opcode == PUTFIELD) {
			switch (popDescType(stack, descToType(desc))) {
			case STRUCT_HI:
				desc = "J";
				break;

			case STRUCT_ARRAY:
				desc = "[J";
				break;

			default:
				break;
			}

			if (opcode == PUTFIELD) {
				stack.popEQ(VarType.REFERENCE);
			}
		} else {
			throw new IllegalStateException();
		}

		if (StructEnv.PRINT_LOG)
			System.out.println("\t2)\t" + opcodeToString(opcode) + " " + owner + " " + name + " " + desc);
		super.visitFieldInsn(opcode, owner, name, desc);
	}

	@Override
	public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
		if (StructEnv.PRINT_LOG)
			System.out.println("\t1)\t" + opcodeToString(opcode) + " " + owner + " " + name + " " + desc);

		String params = desc.substring(desc.indexOf('(') + 1, desc.indexOf(')'));
		String ret = desc.substring(desc.indexOf(')') + 1);

		if (opcode == INVOKESTATIC || //
				opcode == INVOKEVIRTUAL || //
				opcode == INVOKEINTERFACE || //
				opcode == INVOKESPECIAL) {
			String[] arr = splitDescArray(params);
			for (int i = arr.length - 1, ii = 0; i >= 0; i--, ii++) {
				if (stack.peek(ii) == VarType.EMBEDDED_ARRAY) {
					VarStack args = new VarStack(8);
					for (int k = 0; k < arr.length; k++)
						pushDescType(args, descToType(arr[k]));

					throw new UnsupportedCallsiteException("cannot pass embedded array to a method, as the array doesn't exist", //
							callsiteDescription, //
							owner + "." + name + "" + desc, // target
							args.topToString(arr.length), //
							stack.topToString(arr.length));
				}

				if (false) // FIXME, totally broken
					if (stack.peek(ii) == VarType.STRUCT_HI) {
						if (descToType(arr[i]) == A_REFERENCE) {
							if (owner.startsWith("java/util/")) {
								if (owner.endsWith("List") || //
										owner.endsWith("Set") || //
										owner.endsWith("Map") || //
										owner.endsWith("Collection")) {
									throw new UnsupportedOperationException(owner + "." + name + desc + //
											"\nCannot use structs with the Java Collection API and/or generics in general." + //
											"\nYou can generate struct-backed collection-classes using: " + StructGenericsSourcecodeGenerator.class.getSimpleName());
								}
							}

							// RFC from TheAgentD: stringify
							// - passing STRUCT argument to REFERENCE
							// parameter...
							// :o(
							// - supports rewriting up to 4 parameters

							arr[i] = "Ljava/lang/String;";
							if (i == arr.length - 1 || i == arr.length - 3) {
								if (i == arr.length - 3) {
									// ...,struct,b,a
									this.visitInsn(DUP2_X1);
									// ...,b,a,struct,b,a
									this.visitInsn(POP2);
								}

								// ...,b,a,struct
								this.visitMethodInsn(INVOKESTATIC, StructMemory.class.getName().replace('.', '/'), "toString", "(" + StructEnv.wrapped_struct_flag + ")" + arr[i], itf);
								// ...,b,a,string

								if (i == arr.length - 3) {
									this.visitInsn(DUP_X2);
									// ...,string,b,a,string
									this.visitInsn(POP);
									// ...,string,b,a
								}
							} else if (i == arr.length - 2 || i == arr.length - 4) {
								if (i == arr.length - 4) {
									// ...,struct,c,b,a
									this.visitInsn(DUP2_X2);
									// ...,b,a,struct,c,b,a
									this.visitInsn(POP2);
								}

								// ...,b,a,struct,c
								this.visitInsn(SWAP);
								// ...,b,a,c,struct
								this.visitMethodInsn(INVOKESTATIC, StructMemory.class.getName().replace('.', '/'), "toString", "(" + StructEnv.wrapped_struct_flag + ")" + arr[i], itf);
								// ...,b,a,c,string
								this.visitInsn(SWAP);
								// ...,b,a,string,c

								if (i == arr.length - 4) {
									this.visitInsn(DUP2_X2);
									// ...,string,c,b,a,string,c
									this.visitInsn(POP2);
									// ...,string,c,b,a
								}
							} else {
								String msg = "cannot stringify struct @ method parameter " + (i + 1) + "/" + arr.length + ": " + //
										"struct stringification is limited to the last 4 parameters of a method";

								VarStack args = new VarStack(8);
								for (int k = 0; k < arr.length; k++)
									pushDescType(args, descToType(arr[k]));

								throw new UnsupportedCallsiteException(msg, //
										callsiteDescription, // callsite
										owner + "." + name + "" + desc, // target
										args.topToString(arr.length), //
										stack.topToString(arr.length));
							}
						}
					}
			}

			for (int i = arr.length - 1, ii = 0; i >= 0; i--, ii++) {
				if (stack.peek(ii) == VarType.NULL) {
					if (descToType(arr[i]) == A_STRUCT) {
						String msg = "not allowed to pass null-argument to a struct-parameter";

						VarStack args = new VarStack(8);
						for (int k = 0; k < arr.length; k++)
							pushDescType(args, descToType(arr[k]));

						throw new UnsupportedCallsiteException(msg, callsiteDescription, owner + "." + name + "" + desc, args.topToString(arr.length), stack.topToString(arr.length));
					}
				}
			}

			for (int i = arr.length - 1; i >= 0; i--) {
				try {
					popDescType(stack, descToType(arr[i]));
				} catch (Exception exc) {
					throw new IllegalStateException("failed to pop parameter: " + arr[i], exc);
				}
			}

			if (opcode != INVOKESTATIC) {
				stack.popEQ(VarType.REFERENCE);
			}
			pushDescType(stack, descToType(ret));
		} else {
			throw new IllegalStateException("unhandled opcode: " + opcodeToString(opcode));
		}

		if (desc.contains(StructEnv.wrapped_struct_flag)) {
			desc = desc.replace(StructEnv.wrapped_struct_flag, "J");
			if (StructEnv.PRINT_LOG)
				System.out.println("\t2)\t" + opcodeToString(opcode) + " " + owner + " " + name + " " + desc);
		}
		super.visitMethodInsn(opcode, owner, name, desc, itf);
	}

	@Override
	public void visitInvokeDynamicInsn(String name, String desc, Handle bsm, Object... bsmArgs) {
		super.visitInvokeDynamicInsn(name, desc, bsm, bsmArgs);
	}

	Label[] labels = new Label[8];
	VarStack[] stackAtLabel = new VarStack[labels.length];
	VarLocal[] localAtLabel = new VarLocal[labels.length];
	int labelIndex = 0;

	private void ensureLabelIndex() {
		if (labelIndex == labels.length) {
			final int len = labels.length * 2;
			labels = Arrays.copyOf(labels, len);
			stackAtLabel = Arrays.copyOf(stackAtLabel, len);
			localAtLabel = Arrays.copyOf(localAtLabel, len);
		}
	}

	@Override
	public void visitJumpInsn(int opcode, Label label) {
		if (StructEnv.PRINT_LOG)
			System.out.println("\t1)\t" + opcodeToString(opcode));

		switch (opcode) {
		case IFEQ:
		case IFNE:
		case IFLT:
		case IFGE:
		case IFGT:
		case IFLE:
			stack.pop();// EQ(VarType.INT); // can be INT, BOOL, etc
			break;

		case IF_ICMPEQ:
		case IF_ICMPNE:
		case IF_ICMPLT:
		case IF_ICMPGE:
		case IF_ICMPGT:
		case IF_ICMPLE:
			stack.popEQ(VarType.INT);
			stack.popEQ(VarType.INT);
			break;

		case IF_ACMPEQ:
		case IF_ACMPNE: {
			VarType got = stack.popEQ(EnumSet.of(VarType.REFERENCE, VarType.STRUCT_LO, VarType.STRUCT_ARRAY, VarType.NULL));
			if (got == VarType.STRUCT_LO) {
				stack.popEQ(VarType.STRUCT_HI);
				stack.popEQ(VarType.STRUCT_LO);
				stack.popEQ(VarType.STRUCT_HI);

				stack.push(VarType.MISC);
				stack.push(VarType.MISC);
				stack.push(VarType.MISC);
				stack.push(VarType.MISC);
				this.visitInsn(Opcodes.LSUB);
				this.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "signum", "(J)I", false);

				if (opcode == IF_ACMPEQ)
					opcode = IFEQ;
				if (opcode == IF_ACMPNE)
					opcode = IFNE;
				stack.popEQ(VarType.INT);

				if (StructEnv.PRINT_LOG)
					System.out.println("\t2)\t" + opcodeToString(opcode));
			} else {
				stack.popEQ(EnumSet.of(got, VarType.NULL)); // pop 2nd value
			}
			break;
		}

		case IFNULL:
		case IFNONNULL: {
			VarType got = stack.popEQ(EnumSet.of(VarType.REFERENCE, VarType.STRUCT_LO, VarType.STRUCT_ARRAY, VarType.NULL));
			if (got == VarType.STRUCT_LO) {
				stack.popEQ(VarType.STRUCT_HI);

				stack.push(VarType.MISC);
				stack.push(VarType.MISC);
				this.visitInsn(Opcodes.LCONST_0);
				this.visitInsn(Opcodes.LSUB);
				this.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "signum", "(J)I", false);

				if (opcode == IFNULL)
					opcode = IFEQ;
				if (opcode == IFNONNULL)
					opcode = IFNE;
				stack.popEQ(VarType.INT);

				if (StructEnv.PRINT_LOG)
					System.out.println("\t2)\t" + opcodeToString(opcode));
			}
			break;
		}

		case GOTO:
			break; // no-op

		case JSR:
			throw new UnsupportedOperationException();

		default:
			throw new IllegalStateException("unhandled opcode: " + opcodeToString(opcode));
		}

		// whatever we're jumping to, will have the same stack/local as we
		// currently have
		{
			int index = -1;
			for (int i = 0; i < labelIndex; i++)
				if (labels[i] == label)
					index = i;

			if (StructEnv.PRINT_LOG)
				System.out.println("\t\t\t\t" + opcodeToString(opcode) + " label[" + index + "] jump to " + label);

			if (index == -1)
				index = labelIndex++;
			this.ensureLabelIndex();
			labels[index] = label;
			stackAtLabel[index] = stack.copy();
			localAtLabel[index] = local.copy();
		}

		super.visitJumpInsn(opcode, label);
	}

	@Override
	public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
		if (StructEnv.PRINT_LOG)
			System.out.println("\t\tTABLE_SWITCH");

		stack.popEQ(VarType.INT);

		super.visitTableSwitchInsn(min, max, dflt, labels);
	}

	@Override
	public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
		if (StructEnv.PRINT_LOG)
			System.out.println("\t\tLOOKUP_SWITCH");

		stack.popEQ(VarType.INT);

		super.visitLookupSwitchInsn(dflt, keys, labels);
	}

	private static class TryCatchBlock {
		public final Label start, end, handler;
		public final String type;

		public TryCatchBlock(Label start, Label end, Label handler, String type) {
			this.start = start;
			this.end = end;
			this.handler = handler;
			this.type = type;
		}

		@Override
		public String toString() {
			return "[" + start + " - " + end + "] -> " + handler + "," + type;
		}
	}

	private List<TryCatchBlock> tryCatchBlocks = new ArrayList<>();

	@Override
	public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
		TryCatchBlock tryCatchBlock = new TryCatchBlock(start, end, handler, type);
		if (StructEnv.PRINT_LOG)
			System.out.println("\t\t_TRYCATCH " + tryCatchBlock);
		tryCatchBlocks.add(tryCatchBlock);

		super.visitTryCatchBlock(start, end, handler, type);
	}

	private boolean saveOrRestoreStateAtLabel(Label label) {
		int index = -1;
		for (int i = 0; i < labelIndex; i++)
			if (labels[i] == label)
				index = i;

		if (index == -1) {
			index = labelIndex++;
			if (StructEnv.PRINT_LOG)
				System.out.println("\t\t\tsaving label state [" + index + "] <= " + label);
			this.ensureLabelIndex();
			labels[index] = label;
			stackAtLabel[index] = (stack == null) ? null : stack.copy();
			localAtLabel[index] = (local == null) ? null : local.copy();
			return false;
		}

		stack = stackAtLabel[index];
		local = localAtLabel[index];
		if (stack == null || local == null)
			throw new IllegalStateException();
		if (StructEnv.PRINT_LOG)
			System.out.println("\t\t\trestored label state [" + index + "] <= " + label);
		return true;
	}

	@Override
	public void visitLabel(Label label) {
		if (StructEnv.PRINT_LOG)
			System.out.println("\t\t_LABEL <= " + label);

		this.saveOrRestoreStateAtLabel(label);

		for (TryCatchBlock tryCatchBlock : tryCatchBlocks) {
			if (tryCatchBlock.handler == label) {
				if (stack == null) {
					if (!this.saveOrRestoreStateAtLabel(tryCatchBlock.start)) {
						throw new IllegalStateException("failed to find state at [start] of TryCatchBlock: " + tryCatchBlock);
					}
				}
				stack.push(VarType.REFERENCE);
			}
		}

		super.visitLabel(label);
	}

	@Override
	public void visitLdcInsn(Object cst) {
		if (StructEnv.PRINT_LOG)
			System.out.println("\t\tLDC " + cst);

		if (cst instanceof Integer) {
			stack.push(VarType.INT);
		} else if (cst instanceof Float) {
			stack.push(VarType.MISC);
		} else if (cst instanceof Long) {
			stack.push(VarType.MISC);
			stack.push(VarType.MISC);
		} else if (cst instanceof Double) {
			stack.push(VarType.MISC);
			stack.push(VarType.MISC);
		} else if (cst instanceof String) {
			stack.push(VarType.REFERENCE);
		} else if (cst instanceof Type) {
			stack.push(VarType.REFERENCE);
		}
		// else if(cst instanceof Object[]) { // FIXME
		// stack.push(VarType.REFERENCE);
		// }
		else {
			throw new IllegalStateException("cst=" + cst.getClass());
		}

		super.visitLdcInsn(cst);
	}

	@Override
	public void visitIincInsn(int var, int increment) {
		if (StructEnv.PRINT_LOG)
			System.out.println("\t\tIINC");

		local.getEQ(var, VarType.INT);
		var = local.remap(var);

		super.visitIincInsn(var, increment);
	}

	@Override
	public void visitMultiANewArrayInsn(String desc, int dims) {
		super.visitMultiANewArrayInsn(desc, dims);
	}

	@Override
	public void visitLocalVariable(String name, String desc, String signature, Label start, Label end, int index) {
		if (desc.equals(StructEnv.array_wrapped_struct_flag))
			desc = "[J";
		if (desc.equals(StructEnv.wrapped_struct_flag))
			desc = "J";
		// fix localvar slot
		super.visitLocalVariable(name, desc, signature, start, end, index);
	}

	@Override
	public void visitLineNumber(int line, Label start) {
		super.visitLineNumber(line, start);
	}

	@Override
	public void visitMaxs(int maxStack, int maxLocals) {
		super.visitMaxs(maxStack, this.maxLocals);
	}

	@Override
	public void visitEnd() {
		super.visitEnd();
	}

	public static String opcodeToString(int opcode) {
		try {
			for (Field field : Opcodes.class.getFields()) {
				int i = ((Integer) field.get(null)).intValue();
				if (i != opcode)
					continue;
				if (field.getName().startsWith("ACC_"))
					continue;
				if (field.getName().startsWith("H_"))
					continue;
				if (field.getName().startsWith("F_"))
					continue;
				if (field.getName().startsWith("T_"))
					continue;
				if (field.getName().startsWith("V1_"))
					continue;
				if (field.getName().equals("INTEGER"))
					continue;
				if (field.getName().equals("LONG"))
					continue;
				if (field.getName().equals("FLOAT"))
					continue;
				if (field.getName().equals("DOUBLE"))
					continue;
				return field.getName();
			}
			return null;
		} catch (IllegalArgumentException | IllegalAccessException e) {
			throw new IllegalStateException(e);
		}
	}

	private static VarType pushDescType(VarStack stack, int descType) {
		if (descType == NO_WORD) {
			// no-op
			return null;
		}
		if (descType == A_FLOAT) {
			return stack.push(VarType.MISC);
		}
		if (descType == A_LONG_OR_DOUBLE) {
			stack.push(VarType.MISC);
			return stack.push(VarType.MISC);
		}
		if (descType == AN_INT) {
			return stack.push(VarType.INT);
		}
		if (descType == A_STRUCT) {
			stack.push(VarType.STRUCT_HI);
			return stack.push(VarType.STRUCT_LO);
		}
		if (descType == A_STRUCT_ARRAY) {
			return stack.push(VarType.STRUCT_ARRAY);
		}
		if (descType == A_REFERENCE) {
			return stack.push(VarType.REFERENCE);
		}

		throw new IllegalStateException();
	}

	private static VarType popDescType(VarStack stack, int descType) {
		if (descType == NO_WORD) {
			// no-op
			return null;
		}
		if (descType == A_FLOAT) {
			return stack.popEQ(EnumSet.of(VarType.MISC, VarType.INT));
		}
		if (descType == A_LONG_OR_DOUBLE) {
			stack.popEQ(VarType.MISC);
			return stack.popEQ(VarType.MISC);
		}
		if (descType == AN_INT) {
			return stack.popEQ(VarType.INT);
		}
		if (descType == A_REFERENCE) {
			return stack.popEQ(EnumSet.of(VarType.REFERENCE, VarType.STRUCT_ARRAY, VarType.NULL));
		}
		if (descType == A_STRUCT) {
			// return stack.popEQ(EnumSet.of(VarType.REFERENCE,
			// VarType.STRUCT));
			stack.popEQ(VarType.STRUCT_LO);
			return stack.popEQ(VarType.STRUCT_HI);
		}
		if (descType == A_STRUCT_ARRAY) {
			return stack.popEQ(EnumSet.of(VarType.REFERENCE, VarType.STRUCT_ARRAY));
		}

		throw new IllegalStateException();

	}

	private static int setLocalvarFromParam(VarLocal local, int slot, int descType) {
		if (descType == NO_WORD) {
			// no-op
		} else if (descType == A_FLOAT) {
			local.set(slot++, VarType.MISC);
		} else if (descType == A_LONG_OR_DOUBLE) {
			local.set(slot++, VarType.MISC);
			local.set(slot++, VarType.MISC);
		} else if (descType == AN_INT) {
			local.set(slot++, VarType.INT);
		} else if (descType == A_STRUCT) {
			local.set(slot++, VarType.STRUCT_LOCALVAR);
		} else if (descType == A_STRUCT_ARRAY) {
			local.set(slot++, VarType.STRUCT_ARRAY);
		} else if (descType == A_REFERENCE) {
			local.set(slot++, VarType.REFERENCE);
		} else {
			throw new IllegalStateException();
		}
		return slot;
	}

	public String[] splitDescArray(String desc) {
		final String orig = desc;
		List<String> parts = new ArrayList<>();

		try {
			while (!desc.isEmpty()) {
				String type = getFirstType(desc);
				desc = desc.substring(type.length());
				parts.add(type);
			}
		} catch (Exception exc) {
			throw new IllegalStateException(orig, exc);
		}

		return parts.toArray(new String[parts.size()]);
	}

	private String getFirstType(String desc) {
		String c = desc.substring(0, 1);
		int type = descToType(c);
		if (type != A_REFERENCE) {
			return c;
		}

		if (c.equals("[")) {
			return "[" + getFirstType(desc.substring(1));
		}

		if (c.equals("L")) {
			int io = desc.indexOf(';');
			if (io == -1)
				throw new IllegalStateException();
			return desc.substring(0, io + 1);
		}

		throw new IllegalStateException();
	}

	private static int NO_WORD = 11;
	private static int AN_INT = 12;
	private static int A_FLOAT = 13;
	private static int A_LONG_OR_DOUBLE = 14;
	private static int A_STRUCT = 15;
	private static int A_STRUCT_ARRAY = 16;
	private static int A_REFERENCE = 17;

	private int descToType(String desc) {
		if (desc.equals("V"))
			return NO_WORD; // void
		if (desc.equals("Z"))
			return AN_INT;
		if (desc.equals("B"))
			return AN_INT;
		if (desc.equals("C"))
			return AN_INT;
		if (desc.equals("S"))
			return AN_INT;
		if (desc.equals("I"))
			return AN_INT; //
		if (desc.equals("F"))
			return A_FLOAT;
		if (desc.equals("J"))
			return A_LONG_OR_DOUBLE;
		if (desc.equals("D"))
			return A_LONG_OR_DOUBLE;
		if (desc.equals(StructEnv.wrapped_struct_flag))
			return A_STRUCT;
		if (desc.equals("[" + StructEnv.wrapped_struct_flag))
			return A_STRUCT_ARRAY;
		if (desc.startsWith("L"))
			return A_REFERENCE; // type
		if (desc.startsWith("["))
			return A_REFERENCE; // array
		throw new UnsupportedOperationException("unexpected desc: " + desc);
	}
}
