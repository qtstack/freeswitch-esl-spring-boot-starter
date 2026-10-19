package io.github.qtstack.fsesl.spring.boot;

import io.github.qtstack.fsesl.EslClientConfig;
import io.github.qtstack.fsesl.ServerConfig;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties under the {@code freeswitch.esl} prefix.
 */
@ConfigurationProperties(prefix = "freeswitch.esl")
public class FreeSwitchEslProperties {

    private boolean enabled;

    private String password;

    private List<String> events = new ArrayList<>();

    private List<String> customEvents = new ArrayList<>();

    private List<Server> servers = new ArrayList<>(List.of(new Server()));

    private Duration commandTimeout = EslClientConfig.DEFAULT_COMMAND_TIMEOUT;

    private Duration reconnectDelay = EslClientConfig.DEFAULT_RECONNECT_DELAY;

    private Duration connectTimeout = EslClientConfig.DEFAULT_CONNECT_TIMEOUT;

    private int workerThreads;

    private int eventThreads;

    private int eventQueueCapacity = EslClientConfig.DEFAULT_EVENT_QUEUE_CAPACITY;

    private int maxHeaderLineLength = 8192;

    private int maxFrameLength = 1024 * 1024;

    private boolean tcpNoDelay = true;

    private boolean autoStartup = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public List<String> getEvents() {
        return events;
    }

    public void setEvents(List<String> events) {
        this.events = events;
    }

    public List<String> getCustomEvents() {
        return customEvents;
    }

    public void setCustomEvents(List<String> customEvents) {
        this.customEvents = customEvents;
    }

    public List<Server> getServers() {
        return servers;
    }

    public void setServers(List<Server> servers) {
        this.servers = servers;
    }

    public Duration getCommandTimeout() {
        return commandTimeout;
    }

    public void setCommandTimeout(Duration commandTimeout) {
        this.commandTimeout = commandTimeout;
    }

    public Duration getReconnectDelay() {
        return reconnectDelay;
    }

    public void setReconnectDelay(Duration reconnectDelay) {
        this.reconnectDelay = reconnectDelay;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public int getWorkerThreads() {
        return workerThreads;
    }

    public void setWorkerThreads(int workerThreads) {
        this.workerThreads = workerThreads;
    }

    public int getEventThreads() {
        return eventThreads;
    }

    public void setEventThreads(int eventThreads) {
        this.eventThreads = eventThreads;
    }

    public int getEventQueueCapacity() {
        return eventQueueCapacity;
    }

    public void setEventQueueCapacity(int eventQueueCapacity) {
        this.eventQueueCapacity = eventQueueCapacity;
    }

    public int getMaxHeaderLineLength() {
        return maxHeaderLineLength;
    }

    public void setMaxHeaderLineLength(int maxHeaderLineLength) {
        this.maxHeaderLineLength = maxHeaderLineLength;
    }

    public int getMaxFrameLength() {
        return maxFrameLength;
    }

    public void setMaxFrameLength(int maxFrameLength) {
        this.maxFrameLength = maxFrameLength;
    }

    public boolean isTcpNoDelay() {
        return tcpNoDelay;
    }

    public void setTcpNoDelay(boolean tcpNoDelay) {
        this.tcpNoDelay = tcpNoDelay;
    }

    public boolean isAutoStartup() {
        return autoStartup;
    }

    public void setAutoStartup(boolean autoStartup) {
        this.autoStartup = autoStartup;
    }

    EslClientConfig toClientConfig() {
        List<ServerConfig> serverConfigs = (servers == null ? List.<Server>of() : servers).stream()
                .map(server -> new ServerConfig(server.getHost(), server.getPort(), server.getPassword()))
                .toList();
        return new EslClientConfig(
                serverConfigs,
                password,
                new LinkedHashSet<>(events == null ? List.of() : events),
                new LinkedHashSet<>(customEvents == null ? List.of() : customEvents),
                commandTimeout,
                reconnectDelay,
                connectTimeout,
                workerThreads,
                eventThreads,
                eventQueueCapacity,
                maxHeaderLineLength,
                maxFrameLength,
                tcpNoDelay);
    }

    public static class Server {

        private String host = "127.0.0.1";

        private int port = 8021;

        private String password;

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
