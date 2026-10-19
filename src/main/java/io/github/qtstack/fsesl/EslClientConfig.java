package io.github.qtstack.fsesl;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Immutable client configuration.
 *
 * @param servers ESL servers to connect to; at least one server is required
 * @param defaultPassword password used by servers without a server-specific password
 * @param events plain event names subscribed after authentication
 * @param customEvents CUSTOM event subclasses subscribed after authentication
 * @param commandTimeout maximum time to wait for a command response
 * @param reconnectDelay delay before reconnecting after a connection failure
 * @param connectTimeout maximum time to wait for a TCP connection
 * @param workerThreads Netty worker threads; values below one use the runtime default
 * @param eventThreads event listener threads; values below one use the runtime default
 * @param eventQueueCapacity maximum number of events waiting for listener threads
 * @param maxHeaderLineLength maximum length of one ESL header line in bytes
 * @param maxFrameLength maximum length of an ESL message body in bytes
 * @param tcpNoDelay whether to enable {@code TCP_NODELAY}
 */
public record EslClientConfig(
        List<ServerConfig> servers,
        String defaultPassword,
        Set<String> events,
        Set<String> customEvents,
        Duration commandTimeout,
        Duration reconnectDelay,
        Duration connectTimeout,
        int workerThreads,
        int eventThreads,
        int eventQueueCapacity,
        int maxHeaderLineLength,
        int maxFrameLength,
        boolean tcpNoDelay
) {

    public static final Duration DEFAULT_COMMAND_TIMEOUT = Duration.ofSeconds(10);
    public static final Duration DEFAULT_RECONNECT_DELAY = Duration.ofSeconds(5);
    public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    public static final int DEFAULT_EVENT_QUEUE_CAPACITY = 4096;

    public EslClientConfig {
        if (servers == null || servers.isEmpty()) {
            throw new IllegalArgumentException("At least one server is required");
        }
        servers.forEach(server -> Objects.requireNonNull(server, "servers must not contain null"));
        Set<String> addresses = servers.stream()
                .map(ServerConfig::address)
                .collect(Collectors.toUnmodifiableSet());
        if (addresses.size() != servers.size()) {
            throw new IllegalArgumentException("Duplicate server addresses are not allowed");
        }
        servers = List.copyOf(servers);
        defaultPassword = defaultPassword == null ? "" : defaultPassword;
        for (ServerConfig server : servers) {
            if ((server.password() == null || server.password().isBlank()) && defaultPassword.isBlank()) {
                throw new IllegalArgumentException(
                        "A password is required for server " + server.address());
            }
        }
        events = copyEventTokens(events, "events");
        customEvents = copyEventTokens(customEvents, "customEvents");
        commandTimeout = positive(commandTimeout, "commandTimeout", DEFAULT_COMMAND_TIMEOUT);
        reconnectDelay = positive(reconnectDelay, "reconnectDelay", DEFAULT_RECONNECT_DELAY);
        connectTimeout = positive(connectTimeout, "connectTimeout", DEFAULT_CONNECT_TIMEOUT);
        if (workerThreads < 1) {
            workerThreads = Runtime.getRuntime().availableProcessors();
        }
        if (eventThreads < 1) {
            eventThreads = Runtime.getRuntime().availableProcessors();
        }
        if (eventQueueCapacity < 1) {
            eventQueueCapacity = DEFAULT_EVENT_QUEUE_CAPACITY;
        }
        if (maxHeaderLineLength < 1) {
            maxHeaderLineLength = 8192;
        }
        if (maxFrameLength < 1) {
            maxFrameLength = 1024 * 1024;
        }
    }

    /**
     * Creates a configuration for servers that carry their own passwords.
     */
    public static EslClientConfig of(ServerConfig... servers) {
        return of(null, servers);
    }

    /**
     * Creates a configuration with a password shared by servers that do not carry their own.
     */
    public static EslClientConfig of(String defaultPassword, ServerConfig... servers) {
        return new EslClientConfig(
                List.of(servers), defaultPassword, Set.of(), Set.of(), DEFAULT_COMMAND_TIMEOUT,
                DEFAULT_RECONNECT_DELAY, DEFAULT_CONNECT_TIMEOUT, 0, 0,
                DEFAULT_EVENT_QUEUE_CAPACITY, 8192, 1024 * 1024, true);
    }

    public String passwordFor(ServerConfig server) {
        return server.password() == null || server.password().isBlank()
                ? defaultPassword
                : server.password();
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static Set<String> copyEventTokens(Set<String> values, String name) {
        if (values == null) {
            return Set.of();
        }
        LinkedHashSet<String> copied = new LinkedHashSet<>();
        values.forEach(value -> copied.add(requireEventToken(value, name)));
        return Collections.unmodifiableSet(copied);
    }

    private static String requireEventToken(String value, String name) {
        String token = requireText(value, name);
        if (token.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException(name + " must be a single token: " + token);
        }
        return token;
    }

    private static Duration positive(Duration value, String name, Duration fallback) {
        if (value == null) {
            return fallback;
        }
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
