package test.net.indiespot.demo.softlylit.structs.support;

import static org.lwjgl.opengl.ARBShaderObjects.*;
import static org.lwjgl.opengl.ARBFramebufferObject.*;
import static org.lwjgl.opengl.GL11.GL_FALSE;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_COMPLETE;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_INCOMPLETE_DRAW_BUFFER;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_INCOMPLETE_READ_BUFFER;
import static org.lwjgl.opengl.GL30.glCheckFramebufferStatus;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;

public class OpenGL {
	public static void checkFboStatus() {
		int framebuffer = glCheckFramebufferStatus(GL_FRAMEBUFFER);
		switch (framebuffer) {
		case GL_FRAMEBUFFER_COMPLETE:
			break;
		case GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT:
			throw new RuntimeException("FrameBuffer has caused a GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT_EXT exception");
		case GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT:
			throw new RuntimeException("FrameBuffer has caused a GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT_EXT exception");
			//case GL_FRAMEBUFFER_INCOMPLETE_DIMENSIONS:
			//	throw new RuntimeException("FrameBuffer has caused a GL_FRAMEBUFFER_INCOMPLETE_DIMENSIONS_EXT exception");
		case GL_FRAMEBUFFER_INCOMPLETE_DRAW_BUFFER:
			throw new RuntimeException("FrameBuffer has caused a GL_FRAMEBUFFER_INCOMPLETE_DRAW_BUFFER_EXT exception");
			//case GL_FRAMEBUFFER_INCOMPLETE_FORMATS:
			//	throw new RuntimeException("FrameBuffer has caused a GL_FRAMEBUFFER_INCOMPLETE_FORMATS_EXT exception");
		case GL_FRAMEBUFFER_INCOMPLETE_READ_BUFFER:
			throw new RuntimeException("FrameBuffer has caused a GL_FRAMEBUFFER_INCOMPLETE_READ_BUFFER_EXT exception");
		default:
			throw new RuntimeException("Unexpected reply from glCheckFramebufferStatusEXT: " + framebuffer);
		}
	}

	public static int createProgram1(int vertShader, int fragShader) {
		int program = glCreateProgramObjectARB();

		glAttachObjectARB(program, vertShader);
		glAttachObjectARB(program, fragShader);

		glLinkProgramARB(program);
		if(glGetObjectParameteriARB(program, GL_OBJECT_LINK_STATUS_ARB) == GL_FALSE) {
			throw new IllegalStateException(getLogInfo(program));
		}

		return program;
	}

	public static int createProgram2(int program) {

		glValidateProgramARB(program);
		if(glGetObjectParameteriARB(program, GL_OBJECT_VALIDATE_STATUS_ARB) == GL_FALSE) {
			throw new IllegalStateException(getLogInfo(program));
		}

		return program;
	}

	public static int createShader(String filename, int shaderType) throws Exception {
		int shader = 0;
		try {
			shader = glCreateShaderObjectARB(shaderType);

			if(shader == 0)
				return 0;

			glShaderSourceARB(shader, readFileAsString(filename));
			glCompileShaderARB(shader);

			if(glGetObjectParameteriARB(shader, GL_OBJECT_COMPILE_STATUS_ARB) == GL_FALSE)
				throw new RuntimeException("Error creating shader: " + getLogInfo(shader));

			return shader;
		}
		catch (Exception exc) {
			glDeleteObjectARB(shader);
			throw exc;
		}
	}

	public static String getLogInfo(int obj) {
		return glGetInfoLogARB(obj, glGetObjectParameteriARB(obj, GL_OBJECT_INFO_LOG_LENGTH_ARB));
	}

	public static String readFileAsString(String filename) throws Exception {
		StringBuilder source = new StringBuilder();

		FileInputStream in = new FileInputStream(filename);

		Exception exception = null;

		BufferedReader reader;
		try {
			reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));

			Exception innerExc = null;
			try {
				String line;
				while ((line = reader.readLine()) != null)
					source.append(line).append('\n');
			}
			catch (Exception exc) {
				exception = exc;
			}
			finally {
				try {
					reader.close();
				}
				catch (Exception exc) {
					if(innerExc == null)
						innerExc = exc;
					else
						exc.printStackTrace();
				}
			}

			if(innerExc != null)
				throw innerExc;
		}
		catch (Exception exc) {
			exception = exc;
		}
		finally {
			try {
				in.close();
			}
			catch (Exception exc) {
				if(exception == null)
					exception = exc;
				else
					exc.printStackTrace();
			}

			if(exception != null)
				throw exception;
		}

		return source.toString();
	}
}
