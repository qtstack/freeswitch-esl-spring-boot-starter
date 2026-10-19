package io.github.qtstack.fsesl.transport;

import io.github.qtstack.fsesl.EslHeaders;

import java.util.Objects;

/**
 * The outcome of an ESL command.
 *
 * @param command the command sent to FreeSWITCH
 * @param response the raw protocol response
 */
public record CommandResponse(String command, EslMessage response) {

    public CommandResponse {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(response, "response");
    }

    public boolean isOk() {
        String replyText = replyText();
        return replyText != null && replyText.startsWith("+OK");
    }

    public String replyText() {
        return response.header(EslHeaders.REPLY_TEXT);
    }
}
