package net.indiespot.struct;

public class Test {
	public static void main(String[] args) {
		System.out.println("Java version: " + System.getProperty("java.version"));
		System.out.println("JVM name: " + System.getProperty("java.vm.name"));
		System.out.println("JVM version: " + System.getProperty("java.vm.version"));
		System.out.println();

		int intColor = 0xff800001 & 0xfeffffff;
		float floatColor = Float.intBitsToFloat(intColor);
		int intColor2 = Float.floatToRawIntBits(floatColor);
		System.out.println(Integer.toHexString(intColor)); // ff800001
		System.out.println(Integer.toHexString(intColor2)); // ffc00001
	}
}
