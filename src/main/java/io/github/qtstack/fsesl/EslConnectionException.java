package io.github.qtstack.fsesl;

/**
 * Thrown when a command cannot be sent because the connection is unavailable.
 */
public class EslConnectionException extends EslClientException {

    public EslConnectionException(String message) {
        super(message);
    }

    public EslConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
