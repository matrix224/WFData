package wfDataModel.model.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to specify a config field and what may be required of it
 * @author MatNova
 *
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface SettingData {

	public String cfgName(); // Field name as it appears in config file
	public Class<?> wrapper() default String.class; // Wrapper for what data type this field is
	public String defValue() default ""; // Default value
	public double minValue() default Double.NaN; // For numeric types, minimum value allowed
	public double maxValue() default Double.NaN; // For numeric types, maximum value allowed
	public boolean required() default false; // If this must be set
}
