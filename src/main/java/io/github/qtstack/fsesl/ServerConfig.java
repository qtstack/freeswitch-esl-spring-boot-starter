package io.github.qtstack.fsesl;

/**
 * Address and optional server-specific password.
 */
public record ServerConfig(String host, int port, String password) {

    public ServerConfig {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("host must not be blank");
        }
        host = host.trim();
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1).trim();
            if (host.isEmpty()) {
                throw new IllegalArgumentException("host must not be blank");
            }
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
    }

    public static ServerConfig of(String host, int port) {
        return new ServerConfig(host, port, null);
    }

    /**
     * Returns the stable address key used by the public client API. IPv6 hosts are wrapped in
     * brackets so that the port remains unambiguous.
     */
    public String address() {
        if (host.startsWith("[") || host.indexOf(':') < 0) {
            return host + ":" + port;
        }
        return "[" + host + "]:" + port;
    }
}
