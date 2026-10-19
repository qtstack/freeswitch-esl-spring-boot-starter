package io.github.qtstack.fsesl.internal;

import io.github.qtstack.fsesl.EslClientConfig;
import io.github.qtstack.fsesl.EslConnectionListener;
import io.github.qtstack.fsesl.EslEventListener;
import io.github.qtstack.fsesl.EslHeaders;
import io.github.qtstack.fsesl.ServerConfig;
import io.github.qtstack.fsesl.transport.EslEvent;
import io.github.qtstack.fsesl.transport.EslMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class NettyEslClientIntegrationTest {

    private static final String REMOTE_TEST_ENABLED = "freeswitch.esl.it.enabled";
    private static final String REMOTE_TEST_HOST = "freeswitch.esl.it.host";
    private static final String REMOTE_TEST_PORT = "freeswitch.esl.it.port";
    private static final String REMOTE_TEST_PASSWORD = "freeswitch.esl.it.password";
    private static final String TEST_PASSWORD = "test-password";

    @Test
    void authenticatesSubscribesCommandsDispatchesEventsAndReconnects() throws Exception {
        RecordingEventListener eventListener = new RecordingEventListener();
        RecordingConnectionListener firstConnectionListener = new RecordingConnectionListener();
        RecordingConnectionListener secondConnectionListener = new RecordingConnectionListener();
        try (PseudoFreeSwitchServer server = new PseudoFreeSwitchServer();
             NettyEslClient client = new NettyEslClient(
                     new EslClientConfig(
                             List.of(new ServerConfig("127.0.0.1", server.port(), null)),
                             TEST_PASSWORD,
                             Set.of("HEARTBEAT"),
                             Set.of("callcenter::info"),
                             Duration.ofSeconds(2),
                             Duration.ofMillis(50),
                             Duration.ofSeconds(2),
                             1,
                             1,
                             128,
                             8192,
                             1024 * 1024,
                             true),
                     List.of(eventListener),
                     List.of(firstConnectionListener, secondConnectionListener))) {

            client.start();

            await().atMost(3, TimeUnit.SECONDS).until(() -> eventListener.events.size() >= 2);
            assertThat(server.commands())
                    .contains("auth " + TEST_PASSWORD,
                            "event plain HEARTBEAT CUSTOM callcenter::info");

            String apiResult = client
                    .sendApiCommand("127.0.0.1:" + server.port(), "status", "")
                    .get(2, TimeUnit.SECONDS)
                    .bodyLines()
                    .get(0);
            assertThat(apiResult).isEqualTo("UP 0 years, 1 day");

            String jobUuid = client
                    .sendBackgroundApiCommand("127.0.0.1:" + server.port(), "status", "")
                    .get(2, TimeUnit.SECONDS);
            assertThat(jobUuid).isEqualTo("5ea6a6a4-5cd0-11ef-9d36-00155d8a0d00");

            server.closeCurrentConnection();
            await().atMost(3, TimeUnit.SECONDS).until(() -> server.authenticatedCount() >= 2);
            await().atMost(3, TimeUnit.SECONDS).until(() -> eventListener.events.size() >= 4);
            assertThat(firstConnectionListener.authenticated()).isGreaterThanOrEqualTo(2);
            assertThat(secondConnectionListener.authenticated()).isGreaterThanOrEqualTo(2);
            assertThat(server.commands()).contains("api status", "bgapi status");

            client.stop();
        }
    }

    @Test
    @EnabledIfSystemProperty(named = REMOTE_TEST_ENABLED, matches = "true")
    void remoteFreeSwitchAcceptsAuthenticationAndCommands() throws Exception {
        String host = requiredSystemProperty(REMOTE_TEST_HOST);
        int port = Integer.parseInt(systemProperty(REMOTE_TEST_PORT, "8021"));
        String password = requiredSystemProperty(REMOTE_TEST_PASSWORD);
        String address = host + ":" + port;
        RecordingConnectionListener connectionListener = new RecordingConnectionListener();

        try (NettyEslClient client = new NettyEslClient(
                new EslClientConfig(
                        List.of(new ServerConfig(host, port, password)),
                        password,
                        Set.of(),
                        Set.of(),
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(2),
                        Duration.ofSeconds(5),
                        1,
                        1,
                        128,
                        8192,
                        1024 * 1024,
                        true),
                List.of(),
                connectionListener)) {

            client.start();
            await().atMost(10, TimeUnit.SECONDS)
                    .until(() -> connectionListener.authenticated() >= 1);

            String status = client
                    .sendApiCommand(address, "status", "")
                    .get(5, TimeUnit.SECONDS)
                    .bodyLines()
                    .stream()
                    .reduce("", (left, right) -> left + right + "\n");
            assertThat(status).contains("UP", "FreeSWITCH");
            assertThat(client.connections()).hasSize(1);
            assertThat(client.connections().get(0).phase())
                    .isEqualTo(io.github.qtstack.fsesl.ServerConnection.Phase.AUTHENTICATED);
        }
    }

    @Test
    void readsJobUuidFromHeaderOrReplyText() {
        assertThat(NettyEslClient.jobUuid(EslMessage.of(
                Map.of(EslHeaders.JOB_UUID, "from-header"), List.of())))
                .isEqualTo("from-header");
        assertThat(NettyEslClient.jobUuid(EslMessage.of(
                Map.of(EslHeaders.REPLY_TEXT, "+OK Job-UUID: from-reply"), List.of())))
                .isEqualTo("from-reply");
        assertThat(NettyEslClient.jobUuid(EslMessage.of(Map.of(), List.of()))).isNull();
    }

    private static String systemProperty(String name, String defaultValue) {
        String value = System.getProperty(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String requiredSystemProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("System property " + name + " must be set");
        }
        return value;
    }

    private static final class RecordingEventListener implements EslEventListener {

        private final List<String> events = new ArrayList<>();

        @Override
        public synchronized void onEvent(String address, EslEvent event) {
            String subclass = event.eventSubclass();
            events.add(address + ":" + event.eventName() + (subclass == null ? "" : ":" + subclass));
        }
    }

    private static final class RecordingConnectionListener implements EslConnectionListener {

        private final AtomicInteger authenticated = new AtomicInteger();

        @Override
        public void onAuthenticated(String address) {
            authenticated.incrementAndGet();
        }

        int authenticated() {
            return authenticated.get();
        }
    }

    private static final class PseudoFreeSwitchServer implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newSingleThreadExecutor(
                runnable -> new Thread(runnable, "pseudo-free-switch"));
        private final ConcurrentLinkedQueue<String> commands = new ConcurrentLinkedQueue<>();
        private final AtomicInteger authenticated = new AtomicInteger();
        private final AtomicBoolean closed = new AtomicBoolean();
        private volatile Socket currentConnection;

        private PseudoFreeSwitchServer() throws IOException {
            serverSocket = new ServerSocket(0);
            executor.submit(this::acceptLoop);
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        List<String> commands() {
            return List.copyOf(commands);
        }

        int authenticatedCount() {
            return authenticated.get();
        }

        void closeCurrentConnection() throws IOException {
            Socket connection = currentConnection;
            if (connection != null) {
                connection.close();
            }
        }

        private void acceptLoop() {
            while (!closed.get()) {
                try (Socket socket = serverSocket.accept()) {
                    currentConnection = socket;
                    serveConnection(socket);
                }
                catch (IOException exception) {
                    // Closing the current socket can interrupt a blocked read. The server
                    // intentionally stays available for the client's next reconnect.
                }
            }
        }

        private void serveConnection(Socket socket) throws IOException {
            socket.setTcpNoDelay(true);
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    socket.getOutputStream(), StandardCharsets.UTF_8));
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.UTF_8));

            writeMessage(writer, "Content-Type: auth/request", null);

            List<String> commandLines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    commandLines.add(line);
                    continue;
                }
                handleCommand(writer, String.join("\n", commandLines));
                commandLines.clear();
            }
        }

        private void handleCommand(BufferedWriter writer, String command) throws IOException {
            commands.add(command);
            if (command.equals("auth " + TEST_PASSWORD)) {
                authenticated.incrementAndGet();
                writeMessage(writer, "Content-Type: command/reply\r\nReply-Text: +OK accepted", null);
            }
            else if (command.startsWith("auth ")) {
                writeMessage(writer, "Content-Type: command/reply\r\nReply-Text: -ERR invalid", null);
            }
            else if (command.startsWith("event plain")) {
                writeMessage(writer,
                        "Content-Type: command/reply\r\nReply-Text: +OK event listener enabled", null);
                sendHeartbeat(writer);
                sendCustomEvent(writer);
            }
            else if (command.equals("api status")) {
                writeMessage(writer, "Content-Type: api/response", "UP 0 years, 1 day");
            }
            else if (command.equals("bgapi status")) {
                writeMessage(writer,
                        "Content-Type: command/reply\r\nReply-Text: +OK Job-UUID: "
                                + "5ea6a6a4-5cd0-11ef-9d36-00155d8a0d00",
                        null);
            }
            else {
                writeMessage(writer, "Content-Type: command/reply\r\nReply-Text: -ERR unknown command", null);
            }
        }

        private void sendHeartbeat(BufferedWriter writer) throws IOException {
            writeMessage(writer,
                    "Content-Type: text/event-plain",
                    "Event-Name: HEARTBEAT\nCore-UUID: 0d0e2c04-5cd0-11ef-9d36-00155d8a0d00\n\n");
        }

        private void sendCustomEvent(BufferedWriter writer) throws IOException {
            writeMessage(writer,
                    "Content-Type: text/event-plain",
                    "Event-Name: CUSTOM\nEvent-Subclass: callcenter::info\n"
                            + "Core-UUID: 0d0e2c04-5cd0-11ef-9d36-00155d8a0d00\n\n");
        }

        private void writeMessage(BufferedWriter writer, String headers, String body) throws IOException {
            writer.write(headers);
            writer.write("\r\n");
            if (body == null) {
                writer.write("\r\n");
            }
            else {
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                writer.write("Content-Length: " + bytes.length + "\r\n\r\n");
                writer.write(body);
            }
            writer.flush();
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                executor.shutdownNow();
                try {
                    currentConnection.close();
                }
                catch (IOException ignored) {
                }
                try {
                    serverSocket.close();
                }
                catch (IOException ignored) {
                }
            }
        }
    }
}
