package net.indiespot.struct.transform;

public enum VarType {
	INT, // any int, array index, array length, etc
	MISC, // float, half a long, half a double
	//
	REFERENCE, // a normal java object reference, potentially null
	NULL_REFERENCE, // a reference known to be null
	//
	STRUCT_PLACEHOLDER, // localvar that originally held a struct-reference (32 bit) but redirects I/O to another localvar
	STRUCT_HI, // localvar holding the highest 32 bits of a struct pointer (64 bit)
	STRUCT_LO, // localvar holding the lowest 32 bits of a struct pointer (64 bit)
	STRUCT_ARRAY, // long[], holding pointers to structs
	STRUCT_TYPE, // localvar holding sizeof(type)
	//
	EMBEDDED_ARRAY
}
