package net.indiespot.struct.codegen;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import test.net.indiespot.demo.softlylit.structs.Line;
import test.net.indiespot.demo.softlylit.structs.Point;
import test.net.indiespot.demo.softlylit.structs.Triangle;

public class StructGenericsSourcecodeGenerator {
	public static void main(String[] args) throws Exception {
		List<Class<?>> structTypes = new ArrayList<>();
		structTypes.add(Point.class);
		structTypes.add(Line.class);
		structTypes.add(Triangle.class);

		File srcBaseOutputDir = new File("./src/");
		System.out.println("Source base directory: " + srcBaseOutputDir.getCanonicalPath());

		final String inputType = "List";

		for (Class<?> structType : structTypes) {
			Package p = structType.getPackage();

			String code = gen(p.getName(), inputType, structType);

			File srcOutputDir = new File(srcBaseOutputDir, p.getName().replace('.', '/'));
			File srcOutput = new File(srcOutputDir, structType.getSimpleName() + inputType + ".java");

			System.out.println("Writing sourcecode: " + srcOutput.getPath());

			FileWriter fw = new FileWriter(srcOutput);
			fw.write(code);
			fw.close();
		}
		System.out.println("Done.");
	}

	public static String gen(String packageName, String inputType, Class<?> structType) throws Exception {
		String templatePath = "templates/" + inputType + ".java.txt";
		String structTypeName = structType.getSimpleName();
		InputStream in = StructGenericsSourcecodeGenerator.class.getResourceAsStream(templatePath);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		byte[] buf = new byte[1024];
		while (true) {
			int got = in.read(buf);
			if (got == -1)
				break;
			baos.write(buf, 0, got);
		}
		String text = new String(baos.toByteArray(), "UTF-8");

		// header: package & imports
		text = "package " + packageName + ";\r\n\r\nimport " + structType.getName() + ";\r\n" + text;

		// "(Class<T>)Object.class" --> "Point.class"
		text = text.replace("(Class<T>)Object.class", structTypeName + ".class");

		// "List<T>" --> "PointList"
		text = text.replaceAll("([a-zA-Z]+)\\<T\\>", structTypeName + "$1");

		// "List(" --> "PointList("
		// "public List(" --> "public PointList("
		text = text.replaceAll("([\\r\\n]+[ \\t]*)(((public|private|protected)[ \\t]+))?(" + inputType + ")[ \\t]*\\(", "$1$2" + structTypeName + "$5(");
		text = text.replaceAll("([\\(\\s])T([\\.\\s\\[])", "$1" + structTypeName + "$2");

		return text;
	}
}
