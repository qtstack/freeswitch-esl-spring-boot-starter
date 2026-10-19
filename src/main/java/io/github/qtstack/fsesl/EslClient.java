package io.github.qtstack.fsesl;

import io.github.qtstack.fsesl.transport.CommandResponse;
import io.github.qtstack.fsesl.transport.EslMessage;
import io.github.qtstack.fsesl.transport.SendEvent;
import io.github.qtstack.fsesl.transport.SendMsg;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Public API for the FreeSWITCH inbound ESL client.
 */
public interface EslClient extends AutoCloseable {

    void start();

    void stop();

    @Override
    void close();

    List<ServerConnection> connections();

    CompletableFuture<EslMessage> sendApiCommand(String address, String command, String argument);

    CompletableFuture<String> sendBackgroundApiCommand(String address, String command, String argument);

    /**
     * Subscribes to one or more plain FreeSWITCH event names, for example
     * {@code CHANNEL_CREATE}. Each argument must be a single event-name token.
     */
    CompletableFuture<CommandResponse> subscribeEvents(String address, String... events);

    /**
     * Subscribes to one or more FreeSWITCH CUSTOM event subclasses, for example
     * {@code callcenter::info}. The client combines each subclass with the {@code CUSTOM}
     * event name before sending the ESL {@code event} command.
     */
    CompletableFuture<CommandResponse> subscribeCustomEvents(String address, String... subclasses);

    CompletableFuture<CommandResponse> cancelEventSubscriptions(String address);

    CompletableFuture<CommandResponse> addEventFilter(String address, String header, String value);

    CompletableFuture<CommandResponse> removeEventFilter(String address, String header, String value);

    CompletableFuture<CommandResponse> sendEvent(String address, SendEvent event);

    CompletableFuture<CommandResponse> sendMsg(String address, SendMsg message);

    CompletableFuture<CommandResponse> setLogLevel(String address, String level);

    CompletableFuture<CommandResponse> cancelLogging(String address);

    /** Returns the configured plain event names, excluding CUSTOM subclasses. */
    Set<String> eventNames();

    /** Returns the configured CUSTOM event subclasses. */
    Set<String> customEventSubclasses();
}
