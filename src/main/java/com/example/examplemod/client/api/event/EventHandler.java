package com.example.examplemod.client.api.event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a single-argument method as a listener on the internal {@link EventBus}.
 * The argument type determines which {@link Event} subtype the method receives.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface EventHandler {

    Priority priority() default Priority.NORMAL;

    /**
     * When true, the listener still receives {@link CancellableEvent}s that
     * have already been cancelled by a higher priority listener.
     */
    boolean receiveCancelled() default false;
}
