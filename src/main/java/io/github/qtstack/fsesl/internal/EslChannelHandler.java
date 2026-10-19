package io.github.qtstack.fsesl.internal;

import io.github.qtstack.fsesl.EslConnectionException;
import io.github.qtstack.fsesl.EslHeaders;
import io.github.qtstack.fsesl.ServerConfig;
import io.github.qtstack.fsesl.transport.EslEvent;
import io.github.qtstack.fsesl.transport.EslMessage;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Netty handler for one inbound ESL connection. FreeSWITCH replies to commands in request order,
 * so this class maintains a FIFO of pending commands.
 */
final class EslChannelHandler extends SimpleChannelInboundHandler<EslMessage> {

    private static final Logger log = LoggerFactory.getLogger(EslChannelHandler.class);

    private final Callback callback;
    private final ServerConfig server;
    private final ScheduledExecutorService timeoutScheduler;
    private final long commandTimeoutMillis;
    private final Queue<PendingCommand> pendingCommands = new ConcurrentLinkedQueue<>();
    private volatile Channel channel;
    private volatile Throwable failureCause;

    EslChannelHandler(
            Callback callback,
            ServerConfig server,
            ScheduledExecutorService timeoutScheduler,
            long commandTimeoutMillis
    ) {
        this.callback = Objects.requireNonNull(callback, "callback");
        this.server = Objects.requireNonNull(server, "server");
        this.timeoutScheduler = Objects.requireNonNull(timeoutScheduler, "timeoutScheduler");
        this.commandTimeoutMillis = commandTimeoutMillis;
    }

    @Override
    public void channelActive(ChannelHandlerContext context) {
        channel = context.channel();
        callback.channelActive(this);
        context.fireChannelActive();
    }

    @Override
    public void channelInactive(ChannelHandlerContext context) {
        failPendingCommands(new EslConnectionException("Connection closed: " + server().address()));
        callback.channelInactive(this, failureCause);
        context.fireChannelInactive();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
        log.error("ESL connection error for {}", server().address(), cause);
        failureCause = cause;
        failPendingCommands(new EslConnectionException("Connection failed: " + server().address(), cause));
        context.close();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext context, EslMessage message) {
        String contentType = message.contentType();
        if (contentType == null) {
            log.warn("Received ESL message without Content-Type: {}", message);
            return;
        }

        switch (contentType) {
            case EslHeaders.TEXT_EVENT_PLAIN -> callback.eventReceived(this, EslEvent.from(message));
            case EslHeaders.API_RESPONSE, EslHeaders.COMMAND_REPLY -> completeNext(message);
            case EslHeaders.AUTH_REQUEST -> callback.authRequested(this);
            case EslHeaders.TEXT_DISCONNECT_NOTICE, EslHeaders.TEXT_RUDE_REJECTION -> context.close();
            default -> log.debug("Ignoring ESL message with Content-Type {}: {}", contentType, message);
        }
    }

    CompletableFuture<EslMessage> sendCommand(String command) {
        return write(requireSingleLine(command, "command") + "\n\n");
    }

    CompletableFuture<EslMessage> sendLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("lines must contain at least one line"));
        }
        StringBuilder text = new StringBuilder();
        lines.forEach(line -> text.append(requireSingleLine(line, "line")).append('\n'));
        text.append('\n');
        return write(text.toString());
    }

    ChannelFuture close() {
        Channel current = channel;
        return current == null ? null : current.close();
    }

    ServerConfig server() {
        return server;
    }

    private synchronized CompletableFuture<EslMessage> write(String text) {
        Channel current = channel;
        if (current == null || !current.isActive()) {
            return CompletableFuture.failedFuture(
                    new EslConnectionException("Connection is not active: " + server.address()));
        }

        PendingCommand pending = new PendingCommand();
        pendingCommands.add(pending);
        current.writeAndFlush(text).addListener(writeFuture -> {
            if (writeFuture.cause() != null) {
                pendingCommands.remove(pending);
                pending.completeExceptionally(new EslConnectionException("Write failed", writeFuture.cause()));
            }
        });
        return pending.future();
    }

    private void completeNext(EslMessage message) {
        PendingCommand pending = pendingCommands.poll();
        if (pending == null) {
            log.warn("Received response without a pending command for {}: {}", server().address(), message);
            return;
        }
        pending.complete(message);
    }

    private void failPendingCommands(Throwable cause) {
        List<PendingCommand> failed = new ArrayList<>();
        PendingCommand pending;
        while ((pending = pendingCommands.poll()) != null) {
            failed.add(pending);
        }
        failed.forEach(command -> command.completeExceptionally(cause));
    }

    private static String requireSingleLine(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        if (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new IllegalArgumentException(name + " must not contain line breaks");
        }
        return value;
    }

    interface Callback {

        void channelActive(EslChannelHandler handler);

        void channelInactive(EslChannelHandler handler, Throwable cause);

        void authRequested(EslChannelHandler handler);

        void eventReceived(EslChannelHandler handler, EslEvent event);
    }

    private final class PendingCommand {

        private final CompletableFuture<EslMessage> future = new CompletableFuture<>();
        private final ScheduledFuture<?> timeout;

        private PendingCommand() {
            timeout = timeoutScheduler.schedule(this::timeout, commandTimeoutMillis, TimeUnit.MILLISECONDS);
            future.whenComplete((message, throwable) -> timeout.cancel(false));
        }

        private CompletableFuture<EslMessage> future() {
            return future;
        }

        private void complete(EslMessage message) {
            future.complete(message);
        }

        private void completeExceptionally(Throwable cause) {
            future.completeExceptionally(cause);
        }

        private void timeout() {
            if (pendingCommands.remove(this) && !future.isDone()) {
                future.completeExceptionally(new TimeoutException(
                        "ESL command timed out after " + commandTimeoutMillis + " ms"));
                close();
            }
        }
    }
}
