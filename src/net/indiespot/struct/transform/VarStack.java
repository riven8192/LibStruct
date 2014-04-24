package net.indiespot.struct.transform;

import java.util.EnumSet;

public class VarStack {
	private VarType[] stack;
	private int index;

	public VarStack() {
		stack = new VarType[16];
	}

	public void push(VarType var) {
		try {
			stack[index++] = var;
		}
		finally {
			System.out.println("\t\t\tstack.push(" + var + ") -> " + this);
		}
	}

	public VarType pop() {
		try {
			return stack[--index];
		}
		finally {
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
			System.out.println("\t\t\tstack.peek(" + stack[index - 1 - off] + ") -> " + this);
		}
	}

	public VarStack copy() {
		VarStack copy = new VarStack();
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
		StringBuilder sb = new StringBuilder();
		sb.append('[');
		for(int i = 0; i < index; i++) {
			if(i > 0)
				sb.append(",");
			sb.append(stack[i]);
		}
		sb.append(']');
		return sb.toString();
	}
}
