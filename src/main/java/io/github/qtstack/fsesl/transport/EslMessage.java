package io.github.qtstack.fsesl.transport;

import io.github.qtstack.fsesl.EslHeaders;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An immutable ESL message received from FreeSWITCH.
 *
 * @param headers protocol headers, keyed by their original names
 * @param bodyLines body lines, if the message had a non-zero content length
 */
public record EslMessage(Map<String, String> headers, List<String> bodyLines) {

    public EslMessage {
        Map<String, String> copiedHeaders = new LinkedHashMap<>(headers == null ? Map.of() : headers);
        headers = Collections.unmodifiableMap(copiedHeaders);
        bodyLines = bodyLines == null ? List.of() : List.copyOf(bodyLines);
    }

    public static EslMessage of(Map<String, String> headers, List<String> bodyLines) {
        return new EslMessage(headers, bodyLines);
    }

    public String contentType() {
        return headers.get(EslHeaders.CONTENT_TYPE);
    }

    public boolean hasHeader(String name) {
        return headers.containsKey(name);
    }

    public String header(String name) {
        return headers.get(name);
    }

    public boolean hasContentLength() {
        return hasHeader(EslHeaders.CONTENT_LENGTH);
    }

    public int contentLength() {
        String value = header(EslHeaders.CONTENT_LENGTH);
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        }
        catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid Content-Length: " + value, exception);
        }
    }

    @Override
    public String toString() {
        return "EslMessage[contentType=%s, headers=%d, bodyLines=%d]"
                .formatted(contentType(), headers.size(), bodyLines.size());
    }

    static Builder builder() {
        return new Builder();
    }

    static final class Builder {
        private final Map<String, String> headers = new LinkedHashMap<>();
        private final List<String> bodyLines = new ArrayList<>();

        private Builder() {
        }

        Builder header(String name, String value) {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(value, "value");
            headers.put(name, value);
            return this;
        }

        Builder bodyLine(String line) {
            if (line != null) {
                bodyLines.add(line);
            }
            return this;
        }

        EslMessage build() {
            return new EslMessage(headers, bodyLines);
        }

        Map<String, String> headersView() {
            return Collections.unmodifiableMap(headers);
        }

        String header(String name) {
            return headers.get(name);
        }

        boolean hasContentLength0() {
            return headers.containsKey(EslHeaders.CONTENT_LENGTH);
        }

        int contentLength0() {
            String value = headers.get(EslHeaders.CONTENT_LENGTH);
            try {
                return value == null ? 0 : Integer.parseInt(value.trim());
            }
            catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid Content-Length: " + value, exception);
            }
        }
    }
}
