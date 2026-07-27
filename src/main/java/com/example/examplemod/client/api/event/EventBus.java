package com.example.examplemod.client.api.event;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Lightweight internal publish/subscribe bus, independent from the Forge/FML
 * event buses. {@link com.example.examplemod.client.hook.ForgeEventBridge}
 * translates real game events into instances posted here, so the rest of the
 * client (modules, hud, gui) only ever depends on this API.
 */
public final class EventBus {

    private static final EventBus INSTANCE = new EventBus();

    private final Map<Class<?>, List<Listener>> listeners = new ConcurrentHashMap<>();

    private EventBus() {
    }

    public static EventBus getInstance() {
        return INSTANCE;
    }

    /**
     * Scans every method of {@code subscriber} annotated with {@link EventHandler}
     * that takes a single {@link Event} parameter, and registers it as a listener.
     */
    public void register(Object subscriber) {
        for (Method method : subscriber.getClass().getDeclaredMethods()) {
            EventHandler annotation = method.getAnnotation(EventHandler.class);
            if (annotation == null) {
                continue;
            }
            if (method.getParameterCount() != 1) {
                throw new IllegalStateException("Event handler " + method + " must take exactly one parameter");
            }
            Class<?> eventType = method.getParameterTypes()[0];
            if (!Event.class.isAssignableFrom(eventType)) {
                throw new IllegalStateException("Event handler " + method + " parameter must extend Event");
            }
            method.setAccessible(true);
            try {
                MethodHandle handle = MethodHandles.lookup().unreflect(method);
                if (!Modifier.isStatic(method.getModifiers())) {
                    handle = handle.bindTo(subscriber);
                }
                listeners.computeIfAbsent(eventType, key -> new CopyOnWriteArrayList<>())
                        .add(new Listener(subscriber, handle, annotation));
                sortListeners(eventType);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Could not bind event handler " + method, e);
            }
        }
    }

    public void unregister(Object subscriber) {
        for (List<Listener> list : listeners.values()) {
            list.removeIf(listener -> listener.owner == subscriber);
        }
    }

    /**
     * Dispatches {@code event} to every registered listener for its exact type,
     * highest {@link Priority} first. If {@code event} is a {@link CancellableEvent}
     * and becomes cancelled, listeners that did not opt into
     * {@link EventHandler#receiveCancelled()} are skipped for the remainder of the dispatch.
     */
    public <T extends Event> T post(T event) {
        List<Listener> list = listeners.get(event.getClass());
        if (list == null || list.isEmpty()) {
            return event;
        }
        boolean cancellable = event instanceof CancellableEvent;
        for (Listener listener : list) {
            if (cancellable && ((CancellableEvent) event).isCancelled() && !listener.annotation.receiveCancelled()) {
                continue;
            }
            try {
                listener.handle.invoke(event);
            } catch (Throwable throwable) {
                throw new RuntimeException("Error dispatching " + event.getClass().getSimpleName()
                        + " to " + listener.owner.getClass().getName(), throwable);
            }
        }
        event.setHandled(true);
        return event;
    }

    private void sortListeners(Class<?> eventType) {
        listeners.get(eventType).sort(Comparator.comparingInt((Listener l) -> l.annotation.priority().getWeight()).reversed());
    }

    private static final class Listener {
        private final Object owner;
        private final MethodHandle handle;
        private final EventHandler annotation;

        private Listener(Object owner, MethodHandle handle, EventHandler annotation) {
            this.owner = owner;
            this.handle = handle;
            this.annotation = annotation;
        }
    }
}
