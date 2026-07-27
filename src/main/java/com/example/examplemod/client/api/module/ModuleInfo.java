package com.example.examplemod.client.api.module;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Static metadata for a {@link Module} subclass. Read once by
 * {@link Module}'s constructor via reflection, so implementations only need
 * to declare this annotation instead of repeating boilerplate getters.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ModuleInfo {

    String name();

    String description() default "";

    Category category();

    /** GLFW key code, or -1 (GLFW_KEY_UNKNOWN) for "unbound". */
    int defaultKey() default -1;

    BindType bindType() default BindType.TOGGLE;
}
