package net.indiespot.struct.transform;

import java.util.Arrays;
import java.util.EnumSet;

public class VarStack {
	private VarType[] stack;
	private int index;

	public VarStack() {
		this(8);
	}

	public VarStack(int size) {
		stack = new VarType[size];
	}

	public VarType push(VarType var) {
		if(index == stack.length) {
			stack = Arrays.copyOf(stack, stack.length * 2);
		}

		try {
			return stack[index++] = var;
		}
		finally {
			if(StructEnv.print_log)
				System.out.println("\t\t\tstack.push(" + var + ") -> " + this);
		}
	}

	public VarType pop() {
		try {
			return stack[--index];
		}
		finally {
			if(StructEnv.print_log)
				System.out.println("\t\t\tstack.pop(" + stack[index] + ") -> " + this);
		}
	}

	public VarType peek() {
		return peek(0);
	}

	public VarType peek(int off) {
		try {
			return stack[index - 1 - off];
		}
		finally {
			if(StructEnv.print_log)
				System.out.println("\t\t\tstack.peek(" + stack[index - 1 - off] + ") -> " + this);
		}
	}

	public VarStack copy() {
		VarStack copy = new VarStack(this.stack.length);
		copy.index = this.index;
		System.arraycopy(this.stack, 0, copy.stack, 0, index);
		return copy;
	}

	public VarType popEQ(VarType type) {
		if(type != peek())
			throw new IllegalStateException("found=" + peek() + ", required=" + type);
		return pop();
	}

	public VarType popEQ(EnumSet<VarType> types) {
		if(!types.contains(peek()))
			throw new IllegalStateException("found=" + peek() + ", required=" + types);
		return pop();
	}

	public void popNE(VarType type) {
		if(type == pop())
			throw new IllegalStateException();
	}

	public int size() {
		return index;
	}

	public boolean isEmpty() {
		return index == 0;
	}

	public void eqEmpty() {
		if(!isEmpty())
			throw new IllegalStateException("not empty: stack size = " + index);
	}

	@Override
	public String toString() {
		return topToString(index);
	}

	public String topToString(int amount) {
		StringBuilder sb = new StringBuilder();
		sb.append('[');
		for(int i = index - amount; i < index; i++) {
			if(i > 0)
				sb.append(",");
			sb.append(stack[i]);
		}
		sb.append(']');
		return sb.toString();
	}
}
