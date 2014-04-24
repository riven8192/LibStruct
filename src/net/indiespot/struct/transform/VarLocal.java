package net.indiespot.struct.transform;

public class VarLocal {
	private VarType[] local;

	public VarLocal() {
		local = new VarType[16];
	}

	public void set(int index, VarType var) {
		local[index] = var;
	}

	public VarType get(int index) {
		return local[index];
	}

	public void getEQ(int index, VarType type) {
		if (type != get(index))
			throw new IllegalStateException();
	}

	public VarLocal copy() {
		VarLocal copy = new VarLocal();
		System.arraycopy(this.local, 0, copy.local, 0, this.local.length);
		return copy;
	}
}
