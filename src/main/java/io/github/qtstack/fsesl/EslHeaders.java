package io.github.qtstack.fsesl;

/**
 * Header names and content types used by the FreeSWITCH Event Socket protocol.
 */
public final class EslHeaders {

    public static final String CONTENT_TYPE = "Content-Type";
    public static final String CONTENT_LENGTH = "Content-Length";
    public static final String REPLY_TEXT = "Reply-Text";
    public static final String JOB_UUID = "Job-UUID";

    public static final String AUTH_REQUEST = "auth/request";
    public static final String API_RESPONSE = "api/response";
    public static final String COMMAND_REPLY = "command/reply";
    public static final String TEXT_EVENT_PLAIN = "text/event-plain";
    public static final String TEXT_DISCONNECT_NOTICE = "text/disconnect-notice";
    public static final String TEXT_RUDE_REJECTION = "text/rude-rejection";

    private EslHeaders() {
    }
}
