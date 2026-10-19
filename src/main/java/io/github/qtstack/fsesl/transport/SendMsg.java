package io.github.qtstack.fsesl.transport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A builder for the ESL {@code sendmsg} multi-line command.
 */
public final class SendMsg {

    private final List<String> lines;

    private SendMsg(List<String> lines) {
        this.lines = Collections.unmodifiableList(lines);
    }

    public static SendMsg outbound() {
        return new SendMsg(new ArrayList<>(List.of("sendmsg")));
    }

    public static SendMsg forUuid(String uuid) {
        return new SendMsg(new ArrayList<>(List.of("sendmsg " + uuid)));
    }

    public SendMsg callCommand(String command) {
        return line("call-command: " + command);
    }

    public SendMsg executeApp(String name, String argument) {
        return line("execute-app-name: " + name)
                .line("execute-app-arg: " + argument);
    }

    public SendMsg loops(int loops) {
        return line("loops: " + loops);
    }

    public SendMsg hangupCause(String cause) {
        return line("hangup-cause: " + cause);
    }

    public SendMsg eventLock() {
        return line("event-lock: true");
    }

    public SendMsg line(String line) {
        List<String> updated = new ArrayList<>(lines);
        updated.add(line);
        return new SendMsg(updated);
    }

    public List<String> lines() {
        return lines;
    }
}
