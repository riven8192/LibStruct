package net.indiespot.struct.cp;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.CLASS)
public @interface StructField {
	int offset() default -1;

	boolean embed() default false;

	int elements() default 1;
}
