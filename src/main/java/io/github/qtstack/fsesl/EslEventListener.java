package io.github.qtstack.fsesl;

import io.github.qtstack.fsesl.transport.EslEvent;

/**
 * Listener for all events received from a configured FreeSWITCH server.
 */
public interface EslEventListener {

    void onEvent(String address, EslEvent event);
}
