package io.github.qtstack.fsesl;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EslClientConfigTest {

    private static final String TEST_PASSWORD = "test-password";

    @Test
    void rejectsDuplicateServerAddressesEvenWhenPasswordsDiffer() {
        assertThatThrownBy(() -> new EslClientConfig(
                List.of(
                        new ServerConfig("127.0.0.1", 8021, "one"),
                        new ServerConfig("127.0.0.1", 8021, "two")),
                TEST_PASSWORD,
                Set.of(),
                Set.of(),
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                1,
                1,
                128,
                8192,
                1024,
                true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Duplicate server addresses are not allowed");
    }

    @Test
    void usesDefaultLimitsWhenNullAndRejectsNonPositiveValues() {
        EslClientConfig config = new EslClientConfig(
                List.of(ServerConfig.of("127.0.0.1", 8021)),
                TEST_PASSWORD,
                null,
                null,
                null,
                null,
                null,
                0,
                0,
                0,
                0,
                0,
                true);

        assertThat(config.commandTimeout()).isEqualTo(EslClientConfig.DEFAULT_COMMAND_TIMEOUT);
        assertThat(config.reconnectDelay()).isEqualTo(EslClientConfig.DEFAULT_RECONNECT_DELAY);
        assertThat(config.connectTimeout()).isEqualTo(EslClientConfig.DEFAULT_CONNECT_TIMEOUT);
        assertThat(config.eventQueueCapacity()).isEqualTo(EslClientConfig.DEFAULT_EVENT_QUEUE_CAPACITY);

        assertThatThrownBy(() -> new EslClientConfig(
                List.of(ServerConfig.of("127.0.0.1", 8021)),
                TEST_PASSWORD,
                Set.of(),
                Set.of(),
                Duration.ZERO,
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                1,
                1,
                128,
                8192,
                1024,
                true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("commandTimeout must be positive");
    }

    @Test
    void requiresAPasswordForEveryServer() {
        assertThatThrownBy(() -> new EslClientConfig(
                List.of(ServerConfig.of("127.0.0.1", 8021)),
                null,
                Set.of(),
                Set.of(),
                null,
                null,
                null,
                0,
                0,
                0,
                0,
                0,
                true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A password is required for server 127.0.0.1:8021");
    }

    @Test
    void rejectsCompoundEventTokens() {
        List<ServerConfig> servers = List.of(ServerConfig.of("127.0.0.1", 8021));

        assertThatThrownBy(() -> new EslClientConfig(
                servers, TEST_PASSWORD, Set.of("CUSTOM callcenter::info"), Set.of(), null,
                null, null, 0, 0, 0, 0, 0, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("events must be a single token: CUSTOM callcenter::info");

        assertThatThrownBy(() -> new EslClientConfig(
                servers, TEST_PASSWORD, Set.of(), Set.of("CUSTOM callcenter::info"), null,
                null, null, 0, 0, 0, 0, 0, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("customEvents must be a single token: CUSTOM callcenter::info");
    }

    @Test
    void formatsIpv6AddressesWithBrackets() {
        assertThat(new ServerConfig("::1", 8021, TEST_PASSWORD).address())
                .isEqualTo("[::1]:8021");
        ServerConfig bracketed = new ServerConfig("[::1]", 8021, TEST_PASSWORD);
        assertThat(bracketed.host()).isEqualTo("::1");
        assertThat(bracketed.address()).isEqualTo("[::1]:8021");
    }
}
