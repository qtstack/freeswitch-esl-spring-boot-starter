package io.github.qtstack.fsesl.internal;

import io.github.qtstack.fsesl.EslClient;
import io.github.qtstack.fsesl.EslClientConfig;
import io.github.qtstack.fsesl.EslConnectionException;
import io.github.qtstack.fsesl.EslConnectionListener;
import io.github.qtstack.fsesl.EslEventListener;
import io.github.qtstack.fsesl.EslHeaders;
import io.github.qtstack.fsesl.ServerConfig;
import io.github.qtstack.fsesl.ServerConnection;
import io.github.qtstack.fsesl.transport.CommandResponse;
import io.github.qtstack.fsesl.transport.EslEvent;
import io.github.qtstack.fsesl.transport.EslMessage;
import io.github.qtstack.fsesl.transport.EslFrameDecoder;
import io.github.qtstack.fsesl.transport.SendEvent;
import io.github.qtstack.fsesl.transport.SendMsg;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Netty implementation of {@link EslClient}. Connections authenticate automatically and reconnect
 * with the configured delay until the client is stopped.
 */
public final class NettyEslClient implements EslClient {

    private static final Logger log = LoggerFactory.getLogger(NettyEslClient.class);

    private final EslClientConfig config;
    private final List<EslEventListener> eventListeners;
    private final List<EslConnectionListener> connectionListeners;
    private final ConcurrentHashMap<String, ManagedConnection> connections = new ConcurrentHashMap<>();
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean stopped = new AtomicBoolean();

    private MultiThreadIoEventLoopGroup workerGroup;
    private ScheduledExecutorService timeoutScheduler;
    private ScheduledExecutorService reconnectScheduler;
    private ExecutorService eventExecutor;

    public NettyEslClient(
            EslClientConfig config,
            List<EslEventListener> eventListeners,
            EslConnectionListener connectionListener
    ) {
        this(config, eventListeners, connectionListener == null ? List.of() : List.of(connectionListener));
    }

    public NettyEslClient(
            EslClientConfig config,
            List<EslEventListener> eventListeners,
            List<EslConnectionListener> connectionListeners
    ) {
        this.config = Objects.requireNonNull(config, "config");
        this.eventListeners = List.copyOf(eventListeners == null ? List.of() : eventListeners);
        this.connectionListeners = List.copyOf(connectionListeners == null ? List.of() : connectionListeners);
        config.servers().forEach(server -> connections.put(server.address(), new ManagedConnection(server)));
    }

    @Override
    public void start() {
        if (stopped.get() || !started.compareAndSet(false, true)) {
            return;
        }

        timeoutScheduler = new ScheduledThreadPoolExecutor(
                1, new DefaultThreadFactory("fs-esl-timeout", true));
        reconnectScheduler = new ScheduledThreadPoolExecutor(
                1, new DefaultThreadFactory("fs-esl-reconnect", true));
        eventExecutor = new ThreadPoolExecutor(
                config.eventThreads(),
                config.eventThreads(),
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(config.eventQueueCapacity()),
                new DefaultThreadFactory("fs-esl-events", true),
                new ThreadPoolExecutor.AbortPolicy());
        workerGroup = new MultiThreadIoEventLoopGroup(
                config.workerThreads(), new DefaultThreadFactory("fs-esl-io", true), NioIoHandler.newFactory());

        connections.values().forEach(this::connect);
    }

    @Override
    public void stop() {
        if (!started.get() || !stopped.compareAndSet(false, true)) {
            return;
        }

        connections.values().forEach(ManagedConnection::stop);
        timeoutScheduler.shutdownNow();
        reconnectScheduler.shutdownNow();
        eventExecutor.shutdownNow();
        workerGroup.shutdownGracefully(100, 1000, TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() {
        stop();
    }

    @Override
    public List<ServerConnection> connections() {
        return connections.values().stream()
                .map(connection -> new ServerConnection(connection.server, connection.phase.get()))
                .toList();
    }

    @Override
    public CompletableFuture<EslMessage> sendApiCommand(String address, String command, String argument) {
        return handler(address).sendCommand(commandLine("api", command, argument));
    }

    @Override
    public CompletableFuture<String> sendBackgroundApiCommand(String address, String command, String argument) {
        return handler(address).sendCommand(commandLine("bgapi", command, argument))
                .thenApply(response -> {
                    String jobUuid = jobUuid(response);
                    if (jobUuid == null) {
                        throw new EslConnectionException("bgapi response did not contain Job-UUID");
                    }
                    return jobUuid;
                });
    }

    @Override
    public CompletableFuture<CommandResponse> subscribeEvents(String address, String... events) {
        String eventList = String.join(" ", requireEventTokens(events, "events"));
        return sendCommandResponse(address, "event plain " + eventList);
    }

    @Override
    public CompletableFuture<CommandResponse> subscribeCustomEvents(String address, String... subclasses) {
        String eventList = requireEventTokens(subclasses, "subclasses").stream()
                .map(subclass -> "CUSTOM " + subclass)
                .collect(Collectors.joining(" "));
        return sendCommandResponse(address, "event plain " + eventList);
    }

    @Override
    public CompletableFuture<CommandResponse> cancelEventSubscriptions(String address) {
        return sendCommandResponse(address, "noevents");
    }

    @Override
    public CompletableFuture<CommandResponse> addEventFilter(String address, String header, String value) {
        return sendCommandResponse(
                address,
                "filter " + requireSingleLine(header, "header") + " "
                        + requireSingleLine(value, "value"));
    }

    @Override
    public CompletableFuture<CommandResponse> removeEventFilter(String address, String header, String value) {
        return sendCommandResponse(
                address,
                "filter delete " + requireSingleLine(header, "header") + " "
                        + requireSingleLine(value, "value"));
    }

    @Override
    public CompletableFuture<CommandResponse> sendEvent(String address, SendEvent event) {
        return handler(address).sendLines(event.lines())
                .thenApply(response -> new CommandResponse(event.lines().getFirst(), response));
    }

    @Override
    public CompletableFuture<CommandResponse> sendMsg(String address, SendMsg message) {
        return handler(address).sendLines(message.lines())
                .thenApply(response -> new CommandResponse(message.lines().getFirst(), response));
    }

    @Override
    public CompletableFuture<CommandResponse> setLogLevel(String address, String level) {
        return sendCommandResponse(address, "log " + requireSingleLine(level, "level"));
    }

    @Override
    public CompletableFuture<CommandResponse> cancelLogging(String address) {
        return sendCommandResponse(address, "nolog");
    }

    @Override
    public Set<String> eventNames() {
        return config.events();
    }

    @Override
    public Set<String> customEventSubclasses() {
        return config.customEvents();
    }

    private CompletableFuture<CommandResponse> sendCommandResponse(String address, String command) {
        return handler(address).sendCommand(command)
                .thenApply(response -> new CommandResponse(command, response));
    }

    private EslChannelHandler handler(String address) {
        requireText(address, "address");
        ManagedConnection connection = connections.get(address);
        if (connection == null) {
            throw new IllegalArgumentException("Unknown ESL server address: " + address);
        }
        if (connection.phase.get() != ServerConnection.Phase.AUTHENTICATED) {
            throw new EslConnectionException("Server is not authenticated: " + address);
        }
        EslChannelHandler handler = connection.handler;
        if (handler == null) {
            throw new EslConnectionException("Server is not connected: " + address);
        }
        return handler;
    }

    private void connect(ManagedConnection connection) {
        if (stopped.get()) {
            return;
        }
        connection.phase.set(ServerConnection.Phase.CONNECTING);
        ServerConfig server = connection.server;
        newBootstrap()
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel channel) {
                        channel.pipeline().addLast(
                                new StringEncoder(StandardCharsets.UTF_8),
                                new EslFrameDecoder(config.maxHeaderLineLength(), config.maxFrameLength()),
                                new EslChannelHandler(
                                        new ChannelCallback(),
                                        server,
                                        timeoutScheduler,
                                        config.commandTimeout().toMillis()));
                    }
                })
                .connect(server.host(), server.port())
                .addListener(future -> {
                    if (future.isSuccess()) {
                        log.info("Connected to FreeSWITCH ESL at {}", server.address());
                    }
                    else {
                        connection.phase.set(ServerConnection.Phase.FAILED);
                        log.warn("Connect to {} failed", server.address(), future.cause());
                        connection.scheduleReconnect(() -> connect(connection));
                    }
                });
    }

    private Bootstrap newBootstrap() {
        return new Bootstrap()
                .group(workerGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, config.tcpNoDelay())
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, toIntMillis(config.connectTimeout()));
    }

    private String commandLine(String prefix, String command, String argument) {
        requireText(command, "command");
        String line = prefix + " " + requireSingleLine(command, "command").trim();
        if (argument != null && !argument.isBlank()) {
            line += " " + requireSingleLine(argument, "argument").trim();
        }
        return line;
    }

    static String jobUuid(EslMessage response) {
        String header = response.header(EslHeaders.JOB_UUID);
        if (header != null && !header.isBlank()) {
            return header;
        }
        String replyText = response.header(EslHeaders.REPLY_TEXT);
        if (replyText == null) {
            return null;
        }
        int marker = replyText.indexOf("Job-UUID:");
        if (marker < 0) {
            return null;
        }
        String candidate = replyText.substring(marker + "Job-UUID:".length()).trim();
        if (candidate.isEmpty()) {
            return null;
        }
        int separator = candidate.indexOf(' ');
        int tab = candidate.indexOf('\t');
        if (separator < 0 || (tab >= 0 && tab < separator)) {
            separator = tab;
        }
        return separator < 0 ? candidate : candidate.substring(0, separator);
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    private static String requireSingleLine(String value, String name) {
        requireText(value, name);
        if (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new IllegalArgumentException(name + " must not contain line breaks");
        }
        return value;
    }

    private static List<String> requireEventTokens(String[] values, String name) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException(name + " must contain at least one event token");
        }
        return Arrays.stream(values)
                .peek(value -> {
                    requireText(value, name);
                    if (value.chars().anyMatch(Character::isWhitespace)) {
                        throw new IllegalArgumentException(name + " must be single tokens: " + value);
                    }
                })
                .toList();
    }

    private static int toIntMillis(Duration duration) {
        long millis = duration.toMillis();
        return millis > Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.toIntExact(Math.max(1, millis));
    }

    private final class ChannelCallback implements EslChannelHandler.Callback {

        @Override
        public void channelActive(EslChannelHandler handler) {
            ManagedConnection connection = connections.get(handler.server().address());
            if (connection == null || stopped.get()) {
                handler.close();
                return;
            }
            connection.handler = handler;
            connection.phase.set(ServerConnection.Phase.CONNECTED);
            notifyConnected(connection.server.address());
        }

        @Override
        public void channelInactive(EslChannelHandler handler, Throwable cause) {
            ManagedConnection connection = connections.get(handler.server().address());
            if (connection == null || stopped.get()) {
                return;
            }
            if (connection.handler == handler) {
                connection.handler = null;
            }
            connection.phase.set(ServerConnection.Phase.DISCONNECTED);
            notifyDisconnected(connection.server.address(), cause);
            connection.scheduleReconnect(() -> connect(connection));
        }

        @Override
        public void authRequested(EslChannelHandler handler) {
            ManagedConnection connection = connections.get(handler.server().address());
            if (connection == null || stopped.get()) {
                handler.close();
                return;
            }
            connection.phase.set(ServerConnection.Phase.AUTHENTICATING);
            String password = config.passwordFor(connection.server);
            handler.sendCommand("auth " + password)
                    .whenComplete((response, throwable) -> {
                        if (throwable != null) {
                            connection.phase.set(ServerConnection.Phase.FAILED);
                            log.warn("Authentication to {} failed", connection.server.address(), throwable);
                            handler.close();
                            return;
                        }
                        if (!EslHeaders.COMMAND_REPLY.equals(response.contentType())
                                || response.header(EslHeaders.REPLY_TEXT) == null
                                || !response.header(EslHeaders.REPLY_TEXT).startsWith("+OK")) {
                            connection.phase.set(ServerConnection.Phase.FAILED);
                            log.warn("FreeSWITCH rejected credentials for {}", connection.server.address());
                            handler.close();
                            return;
                        }

                        connection.phase.set(ServerConnection.Phase.AUTHENTICATED);
                        log.info("Authenticated to FreeSWITCH ESL at {}", connection.server.address());
                        notifyAuthenticated(connection.server.address());
                        List<String> subscriptions = new ArrayList<>(config.events());
                        config.customEvents().forEach(
                                subclass -> subscriptions.add("CUSTOM " + subclass));
                        if (!subscriptions.isEmpty()) {
                            sendCommandResponse(
                                    connection.server.address(),
                                    "event plain " + String.join(" ", subscriptions))
                                    .whenComplete((subscriptionResponse, subscriptionThrowable) -> {
                                        if (subscriptionThrowable != null) {
                                            log.warn("Initial event subscription to {} failed",
                                                    connection.server.address(), subscriptionThrowable);
                                        }
                                        else if (!subscriptionResponse.isOk()) {
                                            log.warn("FreeSWITCH rejected initial event subscription to {}: {}",
                                                    connection.server.address(),
                                                    subscriptionResponse.replyText());
                                        }
                                    });
                        }
                    });
        }

        @Override
        public void eventReceived(EslChannelHandler handler, EslEvent event) {
            if (eventListeners.isEmpty()) {
                return;
            }
            String address = handler.server().address();
            try {
                eventExecutor.execute(() -> eventListeners.forEach(listener -> {
                    try {
                        listener.onEvent(address, event);
                    }
                    catch (Throwable throwable) {
                        log.error("ESL event listener failed for {} and {}", address, event, throwable);
                    }
                }));
            }
            catch (RejectedExecutionException exception) {
                if (!stopped.get()) {
                    log.warn("Dropping ESL event for {} because the event queue is full", address);
                }
            }
        }
    }

    private void notifyConnected(String address) {
        connectionListeners.forEach(listener ->
                runConnectionCallback(listener, "onConnected", address, () -> listener.onConnected(address)));
    }

    private void notifyAuthenticated(String address) {
        connectionListeners.forEach(listener -> runConnectionCallback(
                listener, "onAuthenticated", address, () -> listener.onAuthenticated(address)));
    }

    private void notifyDisconnected(String address, Throwable cause) {
        connectionListeners.forEach(listener -> runConnectionCallback(
                listener, "onDisconnected", address, () -> listener.onDisconnected(address, cause)));
    }

    private void runConnectionCallback(
            EslConnectionListener listener,
            String method,
            String address,
            Runnable callback
    ) {
        try {
            callback.run();
        }
        catch (Throwable throwable) {
            log.error(
                    "ESL connection listener {}#{} failed for {}",
                    listener.getClass().getName(),
                    method,
                    address,
                    throwable);
        }
    }

    private final class ManagedConnection {

        private final ServerConfig server;
        private final AtomicReference<ServerConnection.Phase> phase =
                new AtomicReference<>(ServerConnection.Phase.DISCONNECTED);
        private volatile EslChannelHandler handler;
        private volatile ScheduledFuture<?> reconnectTask;
        private ManagedConnection(ServerConfig server) {
            this.server = server;
        }

        private synchronized void scheduleReconnect(Runnable task) {
            if (stopped.get() || phase.get() == ServerConnection.Phase.STOPPED) {
                return;
            }
            if (reconnectTask != null) {
                reconnectTask.cancel(false);
            }
            try {
                reconnectTask = reconnectScheduler.schedule(
                        task, config.reconnectDelay().toMillis(), TimeUnit.MILLISECONDS);
            }
            catch (RejectedExecutionException ignored) {
                // The client is stopping and no further reconnect is required.
            }
        }

        private void stop() {
            phase.set(ServerConnection.Phase.STOPPED);
            ScheduledFuture<?> task = reconnectTask;
            if (task != null) {
                task.cancel(false);
            }
            EslChannelHandler current = handler;
            if (current != null) {
                current.close();
            }
        }
    }
}
