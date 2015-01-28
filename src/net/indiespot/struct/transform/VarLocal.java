package net.indiespot.struct.transform;

import java.util.EnumSet;

public class VarLocal {
	private final int slots;
	private int slotBoundary = -1;
	private final VarType[] local;
	private int[] oldSlot2newSlot;

	public VarLocal(int slots) {
		this.slots = slots;
		this.local = new VarType[slots * 10 + 10];
	}

	public void setupParamsRemapTable(int paramSlots, int slotBoundary) {
		VarType[] orig = local.clone();

		System.out.println("setupParamsRemapTable:" + paramSlots + ":" + slotBoundary);
		this.oldSlot2newSlot = new int[this.local.length];
		int offset = 0;
		for (int i = 0; i < local.length; i++) {
			oldSlot2newSlot[i] = offset++;
			if (local[i] == VarType.STRUCT_LOCALVAR) {
				offset++; // expand
			}
			if (StructEnv.PRINT_LOG)
				if (local[i] != null)
					System.out.println("\t\t\tlocal.map[" + local[i] + "]: " + i + " -> " + oldSlot2newSlot[i]);
		}
		this.slotBoundary = slotBoundary + (oldSlot2newSlot[paramSlots] - paramSlots) + 1;

		this.dump(false);

		for (int src = local.length - 1; src >= 0; src--)
			if (oldSlot2newSlot[src] < local.length)
				local[oldSlot2newSlot[src]] = local[src];

		this.dump(true);

		for (int i = 0; i < local.length; i++)
			if (local[i] == VarType.STRUCT_LOCALVAR)
				local[++i] = null; // in use by struct localvar

		this.dump(true);

		int[] tmp = oldSlot2newSlot;
		oldSlot2newSlot = null;
		for (int i = orig.length - 1; i >= 0; i--) {
			if (orig[i] == VarType.STRUCT_LOCALVAR) {
				System.out.println("for:" + i);
				this.set(i, VarType.STRUCT_LOCALVAR);
			}
		}
		oldSlot2newSlot = tmp;

		this.dump(true);
	}

	public void dump(boolean withMapping) {
		System.out.println("local.dump:");
		for (int i = 0; i < local.length; i++) {
			if (local[i] != null)
				System.out.println("\t\t\tlocal: " + i + " -> " + local[i]);
		}

		if (withMapping)
			for (int i = 0; i < local.length; i++) {
				if (oldSlot2newSlot[i] < local.length)
					if (local[oldSlot2newSlot[i]] != null)
						System.out.println("\t\t\tmapped: " + i + " -> " + oldSlot2newSlot[i] + ": " + local[oldSlot2newSlot[i]]);
			}
	}

	public int remap(int index, VarType var) {
		// if (var == VarType.STRUCT_LOCALVAR)
		// return index;

		if (oldSlot2newSlot != null)
			index = oldSlot2newSlot[index];

		if (var == VarType.STRUCT_HI)
			index = slotBoundary + index * 2 + 0;
		if (var == VarType.STRUCT_LO)
			index = slotBoundary + index * 2 + 1;
		return index;
	}

	public int set(final int index, VarType var) {
		int mapped = this.remap(index, var);

		if (StructEnv.PRINT_LOG)
			System.out.println("\t\t\tlocal.set(" + mapped + ", " + var.name() + ") was " + (local[mapped] == null ? "UNDEFINED" : local[mapped]));
		if (mapped >= local.length)
			throw new ArrayIndexOutOfBoundsException(mapped + "/" + local.length);
		local[mapped] = var;

		if (var == VarType.STRUCT_LOCALVAR && slotBoundary != -1) {
			int base = this.set(index, VarType.STRUCT_HI);
			this.set(index, VarType.STRUCT_LO);
			return base;
		}
		return mapped;
	}

	public int getStructBaseIndex(int index) {
		int mapped = oldSlot2newSlot[index];
		if (local[mapped] == null)
			throw new IllegalStateException();

		if (local[mapped] != VarType.STRUCT_LOCALVAR)
			throw new IllegalStateException();

		return slotBoundary + index * 2 + 0;
	}

	public VarType getOriginal(final int index) {
		return local[index];
	}

	public VarType get(final int index) {
		int mapped = oldSlot2newSlot[index];
		if (local[mapped] == null)
			throw new IllegalStateException();
		VarType var = local[mapped];

		if (var == VarType.STRUCT_LOCALVAR && slotBoundary != -1) {
			if (local[slotBoundary + index * 2 + 0] != VarType.STRUCT_HI)
				throw new IllegalStateException("index=" + index + ", mapped=" + mapped + ", expected STRUCT_HI at " + (slotBoundary + index * 2 + 0));
			if (local[slotBoundary + index * 2 + 1] != VarType.STRUCT_LO)
				throw new IllegalStateException("index=" + index + ", mapped=" + mapped + ", expected STRUCT_LO at " + (slotBoundary + index * 2 + 1));
		}
		return var;
	}

	public VarType getEQ(int index, VarType type) {
		if (type == VarType.STRUCT_LOCALVAR)
			throw new UnsupportedOperationException();
		if (type != get(index))
			throw new IllegalStateException("expected: " + type + ", found: " + get(index));
		return type;
	}

	public VarType getEQ(int index, EnumSet<VarType> types) {
		VarType got = get(index);
		if (types.contains(VarType.STRUCT_LOCALVAR))
			throw new UnsupportedOperationException();
		if (!types.contains(got))
			throw new IllegalStateException("expected: " + types + ", found: " + got);
		return got;
	}

	public VarLocal copy() {
		VarLocal copy = new VarLocal(slots);
		System.arraycopy(this.local, 0, copy.local, 0, this.local.length);
		copy.oldSlot2newSlot = this.oldSlot2newSlot; // can be shared, is read-only
		copy.slotBoundary = this.slotBoundary;
		return copy;
	}
}
