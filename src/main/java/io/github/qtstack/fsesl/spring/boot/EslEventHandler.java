package io.github.qtstack.fsesl.spring.boot;

import io.github.qtstack.fsesl.transport.EslEvent;

/**
 * A targeted Spring event handler. Beans implementing this interface are discovered by the
 * starter and routed according to their {@link EslEventName} annotation.
 */
public interface EslEventHandler {

    void handle(String address, EslEvent event);
}
