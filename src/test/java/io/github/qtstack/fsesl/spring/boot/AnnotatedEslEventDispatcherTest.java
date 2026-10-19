package io.github.qtstack.fsesl.spring.boot;

import io.github.qtstack.fsesl.transport.EslEvent;
import io.github.qtstack.fsesl.transport.EslMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnnotatedEslEventDispatcherTest {

    @Test
    void routesEventsToNamedAndCatchAllHandlers() {
        HeartbeatHandler heartbeat = new HeartbeatHandler();
        ChannelHandler channel = new ChannelHandler();
        CatchAllHandler catchAll = new CatchAllHandler();
        AnnotatedEslEventDispatcher dispatcher =
                new AnnotatedEslEventDispatcher(List.of(heartbeat, channel, catchAll));

        dispatcher.onEvent("127.0.0.1:8021", event("HEARTBEAT"));
        dispatcher.onEvent("127.0.0.1:8021", event("CHANNEL_CREATE"));

        assertThat(heartbeat.events).containsExactly("HEARTBEAT");
        assertThat(channel.events).containsExactly("CHANNEL_CREATE");
        assertThat(catchAll.events).containsExactly("HEARTBEAT", "CHANNEL_CREATE");
    }

    @Test
    void routesCustomEventsBySubclass() {
        CallcenterInfoHandler callcenterInfo = new CallcenterInfoHandler();
        AllCustomEventsHandler allCustomEvents = new AllCustomEventsHandler();
        AnnotatedEslEventDispatcher dispatcher =
                new AnnotatedEslEventDispatcher(List.of(callcenterInfo, allCustomEvents));

        dispatcher.onEvent("127.0.0.1:8021", event("CUSTOM", "callcenter::info"));
        dispatcher.onEvent("127.0.0.1:8021", event("CUSTOM", "conference::maintenance"));

        assertThat(callcenterInfo.events).containsExactly("CUSTOM:callcenter::info");
        assertThat(allCustomEvents.events)
                .containsExactly("CUSTOM:callcenter::info", "CUSTOM:conference::maintenance");
    }

    @Test
    void oneFailingHandlerDoesNotPreventOtherHandlers() {
        FailingHeartbeatHandler failing = new FailingHeartbeatHandler();
        HeartbeatHandler successful = new HeartbeatHandler();
        AnnotatedEslEventDispatcher dispatcher =
                new AnnotatedEslEventDispatcher(List.of(failing, successful));

        dispatcher.onEvent("127.0.0.1:8021", event("HEARTBEAT"));

        assertThat(failing.failures.get()).isEqualTo(1);
        assertThat(successful.events).containsExactly("HEARTBEAT");
    }

    @Test
    void requiresAnnotationOnHandler() {
        assertThatThrownBy(() -> new AnnotatedEslEventDispatcher(List.of(new UnannotatedHandler())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be annotated with @EslEventName");
    }

    private static EslEvent event(String eventName) {
        return event(eventName, null);
    }

    private static EslEvent event(String eventName, String eventSubclass) {
        List<String> bodyLines = new ArrayList<>();
        bodyLines.add("Event-Name: " + eventName);
        if (eventSubclass != null) {
            bodyLines.add("Event-Subclass: " + eventSubclass);
        }
        return EslEvent.from(EslMessage.of(Map.of("Content-Type", "text/event-plain"), bodyLines));
    }

    private abstract static class RecordingHandler implements EslEventHandler {

        final List<String> events = new ArrayList<>();

        @Override
        public void handle(String address, EslEvent event) {
            String subclass = event.eventSubclass();
            events.add(event.eventName() + (subclass == null ? "" : ":" + subclass));
        }
    }

    @EslEventName("HEARTBEAT")
    private static final class HeartbeatHandler extends RecordingHandler {
    }

    @EslEventName({"CHANNEL_CREATE", "CHANNEL_DESTROY"})
    private static final class ChannelHandler extends RecordingHandler {
    }

    @EslEventName
    private static final class CatchAllHandler extends RecordingHandler {
    }

    @EslEventName(subclass = "callcenter::info")
    private static final class CallcenterInfoHandler extends RecordingHandler {
    }

    @EslEventName("CUSTOM")
    private static final class AllCustomEventsHandler extends RecordingHandler {
    }

    @EslEventName("HEARTBEAT")
    private static final class FailingHeartbeatHandler extends RecordingHandler {

        private final AtomicInteger failures = new AtomicInteger();

        @Override
        public void handle(String address, EslEvent event) {
            failures.incrementAndGet();
            throw new IllegalStateException("expected failure");
        }
    }

    private static final class UnannotatedHandler implements EslEventHandler {

        @Override
        public void handle(String address, EslEvent event) {
        }
    }
}
