package net.indiespot.struct.transform;

import java.util.EnumSet;

public class VarLocal {
	private VarType[] local;

	public VarLocal() {
		local = new VarType[16];
	}

	public void set(int index, VarType var) {
		System.out.println("\t\t\tlocal.set("+index+", "+var.name()+")");
		local[index] = var;
	}

	public VarType get(int index) {
		return local[index];
	}

	public void getEQ(int index, VarType type) {
		if(type != get(index))
			throw new IllegalStateException();
	}

	public VarType getEQ(int index, EnumSet<VarType> types) {
		VarType got = get(index);
		if(!types.contains(got))
			throw new IllegalStateException("expected: "+types+", found: "+got);
		return got;
	}

	public VarLocal copy() {
		VarLocal copy = new VarLocal();
		System.arraycopy(this.local, 0, copy.local, 0, this.local.length);
		return copy;
	}
}
