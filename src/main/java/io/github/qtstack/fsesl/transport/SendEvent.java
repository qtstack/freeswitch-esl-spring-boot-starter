package io.github.qtstack.fsesl.transport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A builder for the ESL {@code sendevent} multi-line command.
 */
public final class SendEvent {

    private final List<String> lines;

    private SendEvent(List<String> lines) {
        this.lines = Collections.unmodifiableList(lines);
    }

    public static SendEvent named(String eventName) {
        return new SendEvent(new ArrayList<>(List.of("sendevent " + eventName)));
    }

    public SendEvent header(String name, String value) {
        return line(name + ": " + value);
    }

    public SendEvent body(String bodyLine) {
        return line(bodyLine);
    }

    private SendEvent line(String line) {
        List<String> updated = new ArrayList<>(lines);
        updated.add(line);
        return new SendEvent(updated);
    }

    public List<String> lines() {
        return lines;
    }
}
