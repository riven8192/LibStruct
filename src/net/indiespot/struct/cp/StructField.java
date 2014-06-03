package net.indiespot.struct.cp;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.CLASS)
public @interface StructField {
	int offset();

	int length() default 1;
}
