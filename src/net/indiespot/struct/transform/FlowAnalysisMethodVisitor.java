package net.indiespot.struct.transform;

import static org.objectweb.asm.Opcodes.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class FlowAnalysisMethodVisitor extends MethodVisitor {

	public VarStack stack = new VarStack();
	public VarLocal local = new VarLocal();

	public FlowAnalysisMethodVisitor(MethodVisitor mv, int access, String name, String desc, String signature, String[] exceptions) {
		super(Opcodes.ASM4, mv);

		String params = desc.substring(desc.indexOf('(') + 1, desc.indexOf(')'));
		int slot = 0;
		if((access & Opcodes.ACC_STATIC) == 0) {
			local.set(slot++, VarType.REF); // 'this'
		}
		for(String d : splitDescArray(params)) {
			slot = setDescType(local, slot, descToType(d));
		}
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
		super.visitFrame(type, nLocal, local, nStack, stack);
	}

	@Override
	public void visitInsn(int opcode) {
		System.out.println("\t\t" + opcodeToString(opcode));

		switch (opcode) {
		case ACONST_NULL:
			stack.push(VarType.REF);
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

		case IALOAD:
		case BALOAD:
		case CALOAD:
		case SALOAD:
			stack.popEQ(VarType.INT); // index
			stack.popEQ(VarType.REF); // array
			stack.push(VarType.INT); // value
			break;

		case FALOAD:
			stack.popEQ(VarType.INT); // index
			stack.popEQ(VarType.REF); // array
			stack.push(VarType.MISC); // value
			break;

		case LALOAD:
		case DALOAD:
			stack.popEQ(VarType.INT); // index
			stack.popEQ(VarType.REF); // array
			stack.push(VarType.MISC); // value
			stack.push(VarType.MISC); // value
			break;

		case AALOAD:
			stack.popEQ(VarType.INT); // index
			stack.popEQ(VarType.REF); // array
			stack.push(VarType.REF); // value
			break;

		case IASTORE:
		case BASTORE:
		case CASTORE:
		case SASTORE:
			stack.popEQ(VarType.INT); // value
			stack.popEQ(VarType.INT); // index
			stack.popEQ(VarType.REF); // array
			break;

		case FASTORE:
			stack.popEQ(VarType.MISC); // value
			stack.popEQ(VarType.INT); // index
			stack.popEQ(VarType.REF); // array
			break;

		case LASTORE:
		case DASTORE:
			stack.popEQ(VarType.MISC); // value
			stack.popEQ(VarType.MISC); // value
			stack.popEQ(VarType.INT); // index
			stack.popEQ(VarType.REF); // array
			break;

		case AASTORE:
			stack.popEQ(VarType.REF); // value
			stack.popEQ(VarType.INT); // index
			stack.popEQ(VarType.REF); // array
			break;

		case POP:
			stack.pop();
			break;

		case POP2:
			stack.pop();
			stack.pop();
			break;

		case DUP:
			stack.push(stack.peek());
			break;

		case DUP_X1: {
			VarType got1 = stack.pop();
			VarType got2 = stack.pop();
			stack.push(got1);
			stack.push(got2);
			stack.push(got1);
			break;
		}

		case DUP_X2: {
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
			VarType got1 = stack.pop();
			VarType got2 = stack.pop();
			stack.push(got2);
			stack.push(got1);
			stack.push(got2);
			stack.push(got1);
			break;
		}

		case DUP2_X1: {
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

		case SWAP: // no-op
			break;

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

		case ARETURN:
			stack.popEQ(VarType.REF);
			stack.eqEmpty();
			stack = null;
			local = null;
			break;

		case RETURN:
			stack.eqEmpty();
			stack = null;
			local = null;
			break;

		case ARRAYLENGTH:
			stack.popEQ(VarType.REF);
			stack.push(VarType.INT);
			break;

		case MONITORENTER:
		case MONITOREXIT:
			stack.popEQ(VarType.REF);
			break;

		case ATHROW:
			break;

		default:
			throw new IllegalStateException("unhandled opcode: " + opcodeToString(opcode));
		}

		super.visitInsn(opcode);
	}

	@Override
	public void visitIntInsn(int opcode, int operand) {
		System.out.println("\t\t" + opcodeToString(opcode));

		switch (opcode) {
		case BIPUSH:
		case SIPUSH:
			stack.push(VarType.INT);
			break;

		case NEWARRAY:
			stack.popEQ(VarType.INT); // length
			stack.push(VarType.REF);
			break;

		default:
			throw new IllegalStateException("unhandled opcode: " + opcodeToString(opcode));
		}

		super.visitIntInsn(opcode, operand);
	}

	@Override
	public void visitVarInsn(int opcode, int var) {
		System.out.println("\t\t" + opcodeToString(opcode));

		switch (opcode) {
		case ILOAD:
			local.getEQ(var, VarType.INT);
			stack.push(VarType.INT);
			break;

		case FLOAD:
			local.getEQ(var, VarType.MISC);
			stack.push(VarType.MISC);
			break;

		case LLOAD:
		case DLOAD:
			local.getEQ(var, VarType.MISC);
			local.getEQ(var, VarType.MISC);
			stack.push(VarType.MISC);
			stack.push(VarType.MISC);
			break;

		case ALOAD:
			local.getEQ(var, VarType.REF);
			stack.push(VarType.REF);
			break;

		// ---

		case ISTORE:
			stack.popEQ(VarType.INT);
			local.set(var, VarType.INT);
			break;

		case FSTORE:
			stack.popEQ(VarType.MISC);
			local.set(var, VarType.MISC);
			break;

		case LSTORE:
		case DSTORE:
			stack.popEQ(VarType.MISC);
			stack.popEQ(VarType.MISC);
			local.set(var + 0, VarType.MISC);
			local.set(var + 1, VarType.MISC);
			break;

		case ASTORE:
			stack.popEQ(VarType.REF);
			local.set(var, VarType.REF);
			break;

		case RET:
			throw new UnsupportedOperationException();

		default:
			throw new IllegalStateException("unhandled opcode: " + opcodeToString(opcode));
		}

		super.visitVarInsn(opcode, var);
	}

	@Override
	public void visitTypeInsn(int opcode, String type) {
		System.out.println("\t\t" + opcodeToString(opcode));

		switch (opcode) {
		case NEW:
			stack.push(VarType.REF); // using 'type'
			break;

		case ANEWARRAY:
			stack.popEQ(VarType.INT);// length
			stack.push(VarType.REF); // array
			break;

		case CHECKCAST:
			break; // no-op

		case INSTANCEOF:
			stack.popEQ(VarType.REF);
			stack.push(VarType.INT);
			break;

		default:
			throw new IllegalStateException("unhandled opcode: " + opcodeToString(opcode));
		}

		super.visitTypeInsn(opcode, type);
	}

	@Override
	public void visitFieldInsn(int opcode, String owner, String name, String desc) {
		System.out.println("\t\t" + opcodeToString(opcode));

		int descType = descToType(desc);
		switch (opcode) {
		case GETSTATIC:
			pushDescType(stack, descType);
			break;
		case PUTSTATIC:
			popDescType(stack, descType);
			break;

		case GETFIELD:
			stack.popEQ(VarType.REF);
			pushDescType(stack, descType);
			break;

		case PUTFIELD:
			popDescType(stack, descType);
			stack.popEQ(VarType.REF);
			break;

		default:
			throw new IllegalStateException("unhandled opcode: " + opcodeToString(opcode));
		}

		super.visitFieldInsn(opcode, owner, name, desc);
	}

	@Override
	public void visitMethodInsn(int opcode, String owner, String name, String desc) {
		System.out.println("\t\t" + opcodeToString(opcode) + " " + owner + " " + name + " " + desc);

		String params = desc.substring(desc.indexOf('(') + 1, desc.indexOf(')'));
		String ret = desc.substring(desc.indexOf(')') + 1);

		switch (opcode) {
		case INVOKESPECIAL:
		case INVOKEVIRTUAL:
			for(String d : splitDescArray(params)) {
				popDescType(stack, descToType(d));
			}
			stack.popEQ(VarType.REF);
			pushDescType(stack, descToType(ret));
			break;

		case INVOKESTATIC:
			for(String d : splitDescArray(params)) {
				popDescType(stack, descToType(d));
			}
			pushDescType(stack, descToType(ret));
			break;

		case INVOKEINTERFACE:
			// TODO
			break;

		default:
			throw new IllegalStateException("unhandled opcode: " + opcodeToString(opcode));
		}

		super.visitMethodInsn(opcode, owner, name, desc);
	}

	@Override
	public void visitInvokeDynamicInsn(String name, String desc, Handle bsm, Object... bsmArgs) {
		super.visitInvokeDynamicInsn(name, desc, bsm, bsmArgs);
	}

	Label[] labels = new Label[100];
	VarStack[] stackAtLabel = new VarStack[100];
	VarLocal[] localAtLabel = new VarLocal[100];
	int labelIndex = 0;

	@Override
	public void visitJumpInsn(int opcode, Label label) {
		System.out.println("\t\t" + opcodeToString(opcode));

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
		case IF_ACMPNE:
			stack.popEQ(VarType.REF);
			stack.popEQ(VarType.REF);
			break;

		case IFNULL:
		case IFNONNULL:
			stack.popEQ(VarType.REF);
			break;

		case GOTO:
			break; // no-op

		case JSR:
			throw new UnsupportedOperationException();

		default:
			throw new IllegalStateException("unhandled opcode: " + opcodeToString(opcode));
		}

		{
			int index = -1;
			for(int i = 0; i < labelIndex; i++)
				if(labels[i] == label)
					index = i;

			System.out.println("\t\t\t\t" + opcodeToString(opcode) + " label[" + index + "] jump to " + label);

			if(index == -1)
				index = labelIndex++;
			labels[index] = label;
			stackAtLabel[index] = stack.copy();
			localAtLabel[index] = local.copy();
		}

		super.visitJumpInsn(opcode, label);
	}

	@Override
	public void visitLabel(Label label) {

		int index = -1;
		for(int i = 0; i < labelIndex; i++)
			if(labels[i] == label)
				index = i;

		System.out.println("\t\t\t\tvisit label[" + index + "] <= " + label);

		if(index != -1) {
			stack = stackAtLabel[index];
			local = localAtLabel[index];
		}

		super.visitLabel(label);
	}

	@Override
	public void visitLdcInsn(Object cst) {
		System.out.println("\t\tLDC");

		if(cst instanceof Integer) {
			stack.push(VarType.INT);
		}
		else if(cst instanceof Float) {
			stack.push(VarType.MISC);
		}
		else if(cst instanceof Long) {
			stack.push(VarType.MISC);
			stack.push(VarType.MISC);
		}
		else if(cst instanceof Double) {
			stack.push(VarType.MISC);
			stack.push(VarType.MISC);
		}
		else if(cst instanceof String) {
			stack.push(VarType.REF);
		}
		else if(cst instanceof Type) {
			stack.push(VarType.REF);
		}
		else if(cst instanceof Object[]) { // FIXME
			stack.push(VarType.REF);
		}
		else {
			throw new IllegalStateException("cst=" + cst.getClass());
		}

		super.visitLdcInsn(cst);
	}

	@Override
	public void visitIincInsn(int var, int increment) {
		System.out.println("\t\tIINC");

		local.getEQ(var, VarType.INT);

		super.visitIincInsn(var, increment);
	}

	@Override
	public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
		super.visitTableSwitchInsn(min, max, dflt, labels);
	}

	@Override
	public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
		super.visitLookupSwitchInsn(dflt, keys, labels);
	}

	@Override
	public void visitMultiANewArrayInsn(String desc, int dims) {
		super.visitMultiANewArrayInsn(desc, dims);
	}

	@Override
	public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
		super.visitTryCatchBlock(start, end, handler, type);
	}

	@Override
	public void visitLocalVariable(String name, String desc, String signature, Label start, Label end, int index) {
		// super.visitLocalVariable(name, desc, signature, start, end, index);
	}

	@Override
	public void visitLineNumber(int line, Label start) {
		super.visitLineNumber(line, start);
	}

	@Override
	public void visitMaxs(int maxStack, int maxLocals) {
		super.visitMaxs(maxStack, maxLocals);
	}

	@Override
	public void visitEnd() {
		super.visitEnd();
	}

	public static String opcodeToString(int opcode) {
		try {
			for(Field field : Opcodes.class.getFields()) {
				int i = ((Integer) field.get(null)).intValue();
				if(i != opcode)
					continue;
				if(field.getName().startsWith("ACC_"))
					continue;
				if(field.getName().startsWith("H_"))
					continue;
				if(field.getName().startsWith("F_"))
					continue;
				if(field.getName().startsWith("T_"))
					continue;
				if(field.getName().startsWith("V1_"))
					continue;
				if(field.getName().equals("INTEGER"))
					continue;
				if(field.getName().equals("LONG"))
					continue;
				if(field.getName().equals("FLOAT"))
					continue;
				if(field.getName().equals("DOUBLE"))
					continue;
				return field.getName();
			}
			return null;
		}
		catch (IllegalArgumentException | IllegalAccessException e) {
			throw new IllegalStateException(e);
		}
	}

	private static void pushDescType(VarStack stack, int descType) {
		if(descType == NO_WORD) {
			// no-op
		}
		else if(descType == A_FLOAT) {
			stack.push(VarType.MISC);
		}
		else if(descType == LONG_OR_DOUBLE) {
			stack.push(VarType.MISC);
			stack.push(VarType.MISC);
		}
		else if(descType == AN_INT) {
			stack.push(VarType.INT);
		}
		else if(descType == REFERENCE) {
			stack.push(VarType.REF);
		}
		else {
			throw new IllegalStateException();
		}
	}

	private static void popDescType(VarStack stack, int descType) {
		if(descType == NO_WORD) {
			// no-op
		}
		else if(descType == A_FLOAT) {
			stack.popEQ(EnumSet.of(VarType.MISC, VarType.INT));
		}
		else if(descType == LONG_OR_DOUBLE) {
			stack.popEQ(VarType.MISC);
			stack.popEQ(VarType.MISC);
		}
		else if(descType == AN_INT) {
			stack.popEQ(VarType.INT);
		}
		else if(descType == REFERENCE) {
			stack.popEQ(VarType.REF);
		}
		else {
			throw new IllegalStateException();
		}
	}

	private static int setDescType(VarLocal local, int slot, int descType) {
		if(descType == NO_WORD) {
			// no-op
		}
		else if(descType == A_FLOAT) {
			local.set(slot++, VarType.MISC);
		}
		else if(descType == LONG_OR_DOUBLE) {
			local.set(slot++, VarType.MISC);
			local.set(slot++, VarType.MISC);
		}
		else if(descType == AN_INT) {
			local.set(slot++, VarType.INT);
		}
		else if(descType == REFERENCE) {
			local.set(slot++, VarType.REF);
		}
		else {
			throw new IllegalStateException();
		}
		return slot;
	}

	private static String[] splitDescArray(String desc) {
		List<String> parts = new ArrayList<>();

		while (!desc.isEmpty()) {
			int type = descToType(desc.substring(0, 1));
			if(type == REFERENCE) {
				int io = desc.indexOf(';');
				parts.add(desc.substring(0, io));
				desc = desc.substring(io + 1);
			}
			else {
				parts.add(desc.substring(0, 1));
				desc = desc.substring(1);
			}
		}

		return parts.toArray(new String[parts.size()]);
	}

	private static int NO_WORD = 11;
	private static int AN_INT = 12;
	private static int A_FLOAT = 13;
	private static int LONG_OR_DOUBLE = 14;
	private static int REFERENCE = 15;

	private static int descToType(String desc) {
		if(desc.equals("V"))
			return NO_WORD; // void
		if(desc.equals("Z"))
			return AN_INT;
		if(desc.equals("B"))
			return AN_INT;
		if(desc.equals("C"))
			return AN_INT;
		if(desc.equals("S"))
			return AN_INT;
		if(desc.equals("I"))
			return AN_INT; //
		if(desc.equals("F"))
			return A_FLOAT;
		if(desc.equals("J"))
			return LONG_OR_DOUBLE;
		if(desc.equals("D"))
			return LONG_OR_DOUBLE;
		if(desc.startsWith("L"))
			return REFERENCE; // type
		if(desc.startsWith("["))
			return REFERENCE; // array
		throw new UnsupportedOperationException("unexpected desc: " + desc);
	}
}
