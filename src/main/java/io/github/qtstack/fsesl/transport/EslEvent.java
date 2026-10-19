package io.github.qtstack.fsesl.transport;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An immutable event decoded from a {@code text/event-plain} ESL message.
 */
public final class EslEvent {

    private final Map<String, String> headers;
    private final List<String> bodyLines;

    private EslEvent(Map<String, String> headers, List<String> bodyLines) {
        this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers));
        this.bodyLines = List.copyOf(bodyLines);
    }

    public static EslEvent from(EslMessage message) {
        Map<String, String> headers = new LinkedHashMap<>();
        List<String> bodyLines = new ArrayList<>();
        boolean inBody = false;

        for (String line : message.bodyLines()) {
            if (inBody) {
                if (!line.isEmpty()) {
                    bodyLines.add(line);
                }
                continue;
            }
            if (line.isEmpty()) {
                continue;
            }
            int separator = line.indexOf(':');
            if (separator <= 0 || line.length() <= separator + 1) {
                bodyLines.add(line);
                continue;
            }
            String name = line.substring(0, separator).trim();
            String value = line.substring(separator + 1).trim();
            headers.put(name, decode(value));
            if ("Content-Length".equals(name)) {
                inBody = true;
            }
        }
        return new EslEvent(headers, bodyLines);
    }

    public Map<String, String> headers() {
        return headers;
    }

    public String header(String name) {
        return headers.get(name);
    }

    public String eventName() {
        return header("Event-Name");
    }

    /**
     * Returns the subclass of a {@code CUSTOM} event, or {@code null} for events without one.
     */
    public String eventSubclass() {
        return header("Event-Subclass");
    }

    public String jobUuid() {
        return header("Job-UUID");
    }

    public List<String> bodyLines() {
        return bodyLines;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        return "EslEvent[name=%s, headers=%d, bodyLines=%d]"
                .formatted(eventName(), headers.size(), bodyLines.size());
    }
}
