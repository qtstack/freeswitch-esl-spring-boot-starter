package io.github.qtstack.fsesl;

/**
 * A snapshot of one server connection.
 *
 * @param config server configuration
 * @param phase current lifecycle phase
 */
public record ServerConnection(ServerConfig config, Phase phase) {

    public String address() {
        return config.address();
    }

    public enum Phase {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        AUTHENTICATING,
        AUTHENTICATED,
        FAILED,
        STOPPED
    }
}
