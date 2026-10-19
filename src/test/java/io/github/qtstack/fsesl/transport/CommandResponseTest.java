package io.github.qtstack.fsesl.transport;

import io.github.qtstack.fsesl.EslHeaders;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CommandResponseTest {

    @Test
    void detectsSuccessfulCommandReply() {
        CommandResponse response = new CommandResponse(
                "event plain HEARTBEAT",
                EslMessage.of(Map.of(
                        EslHeaders.CONTENT_TYPE, EslHeaders.COMMAND_REPLY,
                        EslHeaders.REPLY_TEXT, "+OK event listener enabled"
                ), null)
        );

        assertThat(response.isOk()).isTrue();
        assertThat(response.replyText()).isEqualTo("+OK event listener enabled");
    }

    @Test
    void detectsFailedCommandReply() {
        CommandResponse response = new CommandResponse(
                "filter Call-Direction inbound",
                EslMessage.of(Map.of(
                        EslHeaders.CONTENT_TYPE, EslHeaders.COMMAND_REPLY,
                        EslHeaders.REPLY_TEXT, "-ERR command failed"
                ), null)
        );

        assertThat(response.isOk()).isFalse();
    }
}
