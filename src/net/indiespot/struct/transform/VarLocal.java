package net.indiespot.struct.transform;

import java.util.Arrays;
import java.util.EnumSet;

public class VarLocal {
	private VarType[] local;

	public VarLocal() {
		this(8);
	}

	public VarLocal(int size) {
		local = new VarType[size];
	}

	public void set(int index, VarType var) {
		while (index >= local.length) {
			local = Arrays.copyOf(local, local.length * 2);
		}
		if(StructEnv.PRINT_LOG)
			System.out.println("\t\t\tlocal.set(" + index + ", " + var.name() + ")");
		local[index] = var;
	}

	public VarType get(int index) {
		if(index >= local.length || local[index] == null)
			throw new IllegalStateException();
		return local[index];
	}

	public void getEQ(int index, VarType type) {
		if(type != get(index))
			throw new IllegalStateException();
	}

	public VarType getEQ(int index, EnumSet<VarType> types) {
		VarType got = get(index);
		if(!types.contains(got))
			throw new IllegalStateException("expected: " + types + ", found: " + got);
		return got;
	}

	public VarLocal copy() {
		VarLocal copy = new VarLocal(this.local.length);
		System.arraycopy(this.local, 0, copy.local, 0, this.local.length);
		return copy;
	}
}
