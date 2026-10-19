package io.github.qtstack.fsesl.spring.boot;

import io.github.qtstack.fsesl.EslClient;
import io.github.qtstack.fsesl.EslConnectionListener;
import io.github.qtstack.fsesl.EslEventListener;
import io.github.qtstack.fsesl.internal.NettyEslClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for the FreeSWITCH inbound ESL client.
 */
@AutoConfiguration
@EnableConfigurationProperties(FreeSwitchEslProperties.class)
@ConditionalOnClass(EslClient.class)
@ConditionalOnProperty(prefix = "freeswitch.esl", name = "enabled", havingValue = "true")
public class FreeSwitchEslAutoConfiguration {

    @Bean
    @ConditionalOnBean(EslEventHandler.class)
    public EslEventListener eslEventListener(ObjectProvider<EslEventHandler> handlers) {
        return new AnnotatedEslEventDispatcher(handlers.orderedStream().toList());
    }

    @Bean(destroyMethod = "stop")
    @ConditionalOnMissingBean(EslClient.class)
    public EslClient eslClient(
            FreeSwitchEslProperties properties,
            ObjectProvider<EslEventListener> eventListeners,
            ObjectProvider<EslConnectionListener> connectionListeners
    ) {
        return new NettyEslClient(
                properties.toClientConfig(),
                eventListeners.orderedStream().toList(),
                connectionListeners.orderedStream().toList());
    }

    @Bean
    public SmartLifecycle eslClientLifecycle(EslClient client, FreeSwitchEslProperties properties) {
        return EslClientLifecycle.of(client, properties.isAutoStartup());
    }
}
