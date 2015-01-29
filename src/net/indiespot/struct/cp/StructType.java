package net.indiespot.struct.cp;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.CLASS)
public @interface StructType {
	int sizeof() default -1;

	boolean disableClearMemory() default false;
}
