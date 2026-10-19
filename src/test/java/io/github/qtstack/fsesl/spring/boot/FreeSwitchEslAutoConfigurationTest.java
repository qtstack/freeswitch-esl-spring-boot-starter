package io.github.qtstack.fsesl.spring.boot;

import io.github.qtstack.fsesl.EslClient;
import io.github.qtstack.fsesl.EslEventListener;
import io.github.qtstack.fsesl.transport.EslEvent;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FreeSwitchEslAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FreeSwitchEslAutoConfiguration.class));

    @Test
    void doesNotCreateBeansByDefaultOrWhenDisabled() {
        contextRunner.run(context -> {
            assertThat(context.containsBean("eslClient")).isFalse();
            assertThat(context.containsBean("eslClientLifecycle")).isFalse();
            assertThat(context.containsBean("eslEventListener")).isFalse();
        });

        contextRunner
                .withPropertyValues("freeswitch.esl.enabled=false")
                .run(context -> {
                    assertThat(context.containsBean("eslClient")).isFalse();
                    assertThat(context.containsBean("eslClientLifecycle")).isFalse();
                    assertThat(context.containsBean("eslEventListener")).isFalse();
                });
    }

    @Test
    void doesNotStartClientWhenAutoStartupIsDisabled() {
        contextRunner
                .withPropertyValues(
                        "freeswitch.esl.enabled=true",
                        "freeswitch.esl.password=test-password",
                        "freeswitch.esl.auto-startup=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(EslClient.class);
                    EslClientLifecycle lifecycle = context.getBean(EslClientLifecycle.class);
                    assertThat(lifecycle.isAutoStartup()).isFalse();
                    assertThat(lifecycle.isRunning()).isFalse();
                });
    }

    @Test
    void annotatedHandlersAndCustomListenersCanCoexist() {
        contextRunner
                .withPropertyValues(
                        "freeswitch.esl.enabled=true",
                        "freeswitch.esl.password=test-password",
                        "freeswitch.esl.auto-startup=false")
                .withUserConfiguration(HandlerConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(EslClient.class);
                    assertThat(context).hasSingleBean(AnnotatedEslEventDispatcher.class);

                    List<EslEventListener> listeners = context.getBeanProvider(EslEventListener.class)
                            .orderedStream()
                            .toList();
                    assertThat(listeners).hasSize(2);
                    assertThat(listeners).anyMatch(CustomEventListener.class::isInstance);
                    assertThat(listeners).anyMatch(AnnotatedEslEventDispatcher.class::isInstance);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class HandlerConfiguration {

        @Bean
        HeartbeatHandler heartbeatHandler() {
            return new HeartbeatHandler();
        }

        @Bean
        CustomEventListener customEventListener() {
            return new CustomEventListener();
        }
    }

    @EslEventName("HEARTBEAT")
    static class HeartbeatHandler implements EslEventHandler {

        @Override
        public void handle(String address, EslEvent event) {
        }
    }

    static class CustomEventListener implements EslEventListener {

        @Override
        public void onEvent(String address, EslEvent event) {
        }
    }
}
