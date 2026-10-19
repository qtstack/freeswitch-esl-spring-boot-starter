package io.github.qtstack.fsesl.transport;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EslEventTest {

    @Test
    void parsesPlainEventHeadersAndBody() {
        EslEvent event = EslEvent.from(EslMessage.of(
                Map.of("Content-Type", "text/event-plain"),
                List.of(
                        "Event-Name: CHANNEL_CREATE",
                        "Unique-ID: 3f0c9dc0-9e59-4c44-bd64-54d4ca8a1c9e",
                        "Caller-Name: test%20user",
                        "Content-Length: 3",
                        "",
                        "one",
                        "two"
                )
        ));

        assertThat(event.eventName()).isEqualTo("CHANNEL_CREATE");
        assertThat(event.header("Unique-ID")).isEqualTo("3f0c9dc0-9e59-4c44-bd64-54d4ca8a1c9e");
        assertThat(event.header("Caller-Name")).isEqualTo("test user");
        assertThat(event.bodyLines()).containsExactly("one", "two");
    }

    @Test
    void keepsNonHeaderLinesAsBody() {
        EslEvent event = EslEvent.from(EslMessage.of(
                Map.of("Content-Type", "text/event-plain"),
                List.of("Event-Name: MESSAGE", "non-header body")
        ));

        assertThat(event.eventName()).isEqualTo("MESSAGE");
        assertThat(event.bodyLines()).containsExactly("non-header body");
    }
}
