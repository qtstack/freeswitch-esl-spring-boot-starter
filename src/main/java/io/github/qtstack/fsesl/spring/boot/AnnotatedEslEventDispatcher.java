package io.github.qtstack.fsesl.spring.boot;

import io.github.qtstack.fsesl.EslEventListener;
import io.github.qtstack.fsesl.transport.EslEvent;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Dispatches events to beans annotated with {@link EslEventName}.
 */
public final class AnnotatedEslEventDispatcher implements EslEventListener {

    private static final Logger log = LoggerFactory.getLogger(AnnotatedEslEventDispatcher.class);

    private final List<EslEventHandler> catchAllHandlers;
    private final List<HandlerMapping> handlerMappings;

    public AnnotatedEslEventDispatcher(List<EslEventHandler> handlers) {
        Objects.requireNonNull(handlers, "handlers");
        Map<Boolean, List<EslEventHandler>> split = handlers.stream()
                .collect(Collectors.partitioningBy(this::matchesAllEvents));
        catchAllHandlers = List.copyOf(split.get(true));
        handlerMappings = split.get(false).stream()
                .map(handler -> new HandlerMapping(handler, supportedEventKeys(handler)))
                .toList();
    }

    @Override
    public void onEvent(String address, EslEvent event) {
        String eventKey = eventKey(event);
        boolean matched = false;
        for (HandlerMapping mapping : handlerMappings) {
            if (mapping.matches(event)) {
                matched = true;
                dispatch(address, event, mapping.handler());
            }
        }
        if (!catchAllHandlers.isEmpty()) {
            matched = true;
            catchAllHandlers.forEach(handler -> dispatch(address, event, handler));
        }
        if (!matched) {
            log.debug("No handler for ESL event {}", eventKey);
        }
    }

    private boolean matchesAllEvents(EslEventHandler handler) {
        return supportedEventKeys(handler).isEmpty();
    }

    private Set<String> supportedEventKeys(EslEventHandler handler) {
        Class<?> targetClass = ClassUtils.getUserClass(handler.getClass());
        EslEventName annotation = AnnotatedElementUtils.findMergedAnnotation(targetClass, EslEventName.class);
        if (annotation == null) {
            throw new IllegalArgumentException(
                    "EslEventHandler must be annotated with @EslEventName: " + targetClass.getName());
        }
        String[] names = annotation.value();
        String[] subclasses = annotation.subclass();
        if (names == null || subclasses == null) {
            throw new IllegalArgumentException("EslEventName.value and subclass must not be null");
        }
        if (names.length == 0 && subclasses.length > 0) {
            names = new String[] {"CUSTOM"};
        }
        return Arrays.stream(names)
                .flatMap(name -> subclasses.length == 0
                        ? Stream.of(eventKey(name, null))
                        : Arrays.stream(subclasses).map(subclass -> eventKey(name, subclass)))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String eventKey(EslEvent event) {
        String eventName = event.eventName();
        if (eventName == null || eventName.isBlank()) {
            return null;
        }
        return eventKey(eventName, event.eventSubclass());
    }

    private static String eventKey(String eventName, String subclass) {
        requireToken(eventName, "EslEventName.value");
        if (subclass == null || subclass.isBlank()) {
            return eventName;
        }
        requireToken(subclass, "EslEventName.subclass");
        return eventName + " " + subclass;
    }

    private static void requireToken(String value, String name) {
        if (value == null || value.isBlank() || value.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException(name + " must be a single token");
        }
    }

    private void dispatch(String address, EslEvent event, EslEventHandler handler) {
        try {
            handler.handle(address, event);
        }
        catch (Throwable throwable) {
            log.error("ESL event handler {} failed for {} and {}",
                    handler.getClass().getName(), address, event, throwable);
        }
    }

    private record HandlerMapping(EslEventHandler handler, Set<String> eventKeys) {

        private boolean matches(EslEvent event) {
            String eventName = event.eventName();
            if (eventName == null || eventName.isBlank()) {
                return false;
            }
            return eventKeys.contains(eventName)
                    || eventKeys.contains(eventKey(eventName, event.eventSubclass()));
        }
    }
}
