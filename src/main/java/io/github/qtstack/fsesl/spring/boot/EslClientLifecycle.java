package io.github.qtstack.fsesl.spring.boot;

import io.github.qtstack.fsesl.EslClient;
import org.springframework.context.SmartLifecycle;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Starts and stops the ESL client with the Spring application context.
 */
final class EslClientLifecycle implements SmartLifecycle {

    private final EslClient client;
    private final boolean autoStartup;
    private final AtomicBoolean running = new AtomicBoolean();

    private EslClientLifecycle(EslClient client, boolean autoStartup) {
        this.client = client;
        this.autoStartup = autoStartup;
    }

    static EslClientLifecycle of(EslClient client, boolean autoStartup) {
        return new EslClientLifecycle(client, autoStartup);
    }

    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            client.start();
        }
    }

    @Override
    public void stop() {
        if (running.compareAndSet(true, false)) {
            client.stop();
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public boolean isAutoStartup() {
        return autoStartup;
    }
}
