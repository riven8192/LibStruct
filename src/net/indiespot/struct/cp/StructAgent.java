package net.indiespot.struct.cp;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

public class StructAgent {
	public static void premain(String agentArgs, Instrumentation inst) {
		inst.addTransformer(new ClassFileTransformer() {
			@Override
			public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
				try {
					//return StructTransformer.transform(className, classfileBuffer);
					return null;
				}
				catch (Throwable t) {
					t.printStackTrace();
					return null;
				}
			}
		});
	}
}
