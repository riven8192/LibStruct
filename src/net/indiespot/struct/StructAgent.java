package net.indiespot.struct;

import java.lang.instrument.Instrumentation;

public class StructAgent {

	public static void premain(String args, Instrumentation inst) {
		System.out.println("StructAgent: initiating...");
		StructAgentDelegate.premain(args, inst);
	}
}
