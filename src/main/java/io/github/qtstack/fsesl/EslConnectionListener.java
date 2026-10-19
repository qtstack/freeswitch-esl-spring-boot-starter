package io.github.qtstack.fsesl;

/**
 * Listener for inbound connection lifecycle transitions.
 */
public interface EslConnectionListener {

    EslConnectionListener DISABLED = new EslConnectionListener() {
    };

    default void onConnected(String address) {
    }

    default void onAuthenticated(String address) {
    }

    default void onDisconnected(String address, Throwable cause) {
    }
}
