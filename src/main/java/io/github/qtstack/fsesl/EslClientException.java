package io.github.qtstack.fsesl;

/**
 * Base exception for client errors.
 */
public class EslClientException extends RuntimeException {

    public EslClientException(String message) {
        super(message);
    }

    public EslClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
