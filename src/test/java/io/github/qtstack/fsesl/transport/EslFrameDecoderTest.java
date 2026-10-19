package io.github.qtstack.fsesl.transport;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.TooLongFrameException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EslFrameDecoderTest {

    @Test
    void decodesHeaderOnlyFrame() {
        EmbeddedChannel channel = new EmbeddedChannel(new EslFrameDecoder(8192, 1024));

        channel.writeInbound(Unpooled.copiedBuffer(
                "Content-Type: auth/request\r\n\r\n", StandardCharsets.UTF_8));

        EslMessage message = channel.readInbound();
        assertThat(message.contentType()).isEqualTo("auth/request");
        assertThat(message.bodyLines()).isEmpty();
        channel.finishAndReleaseAll();
    }

    @Test
    void decodesFrameWithContentLength() {
        EmbeddedChannel channel = new EmbeddedChannel(new EslFrameDecoder(8192, 1024));
        String body = "Event-Name: HEARTBEAT\n\n";

        channel.writeInbound(Unpooled.copiedBuffer(
                "Content-Type: text/event-plain\nContent-Length: " + body.length()
                        + "\n\n" + body, StandardCharsets.UTF_8));

        EslMessage message = channel.readInbound();
        assertThat(message.contentType()).isEqualTo("text/event-plain");
        assertThat(message.bodyLines()).containsExactly("Event-Name: HEARTBEAT", "");
        channel.finishAndReleaseAll();
    }

    @Test
    void decodesPartialWritesAndMultipleFrames() {
        EmbeddedChannel channel = new EmbeddedChannel(new EslFrameDecoder(8192, 1024));
        String firstPart = "Content-Type: command/reply\nReply-Text: +OK\n";
        String secondPart = "\nContent-Type: api/response\n\n";

        channel.writeInbound(Unpooled.copiedBuffer(firstPart, StandardCharsets.UTF_8));
        assertThat((Object) channel.readInbound()).isNull();

        channel.writeInbound(Unpooled.copiedBuffer(secondPart, StandardCharsets.UTF_8));
        EslMessage first = channel.readInbound();
        EslMessage second = channel.readInbound();

        assertThat(first.contentType()).isEqualTo("command/reply");
        assertThat(first.header("Reply-Text")).isEqualTo("+OK");
        assertThat(second.contentType()).isEqualTo("api/response");
        channel.finishAndReleaseAll();
    }

    @Test
    void resetsBuilderBetweenFrames() {
        EmbeddedChannel channel = new EmbeddedChannel(new EslFrameDecoder(8192, 1024));
        String wire = "Content-Type: a\n\n"
                + "Content-Type: b\nHeader: value\n\n"
                + "Content-Type: c\n\n";

        channel.writeInbound(Unpooled.copiedBuffer(wire, StandardCharsets.UTF_8));

        assertThat(channel.<EslMessage>readInbound().bodyLines()).isEmpty();
        EslMessage second = channel.readInbound();
        assertThat(second.contentType()).isEqualTo("b");
        assertThat(second.header("Header")).isEqualTo("value");
        assertThat(second.bodyLines()).isEmpty();
        assertThat(channel.<EslMessage>readInbound().contentType()).isEqualTo("c");
        channel.finishAndReleaseAll();
    }

    @Test
    void rejectsContentLengthAboveLimitImmediately() {
        EmbeddedChannel channel = new EmbeddedChannel(new EslFrameDecoder(8192, 16));

        assertThatThrownBy(() -> channel.writeInbound(Unpooled.copiedBuffer(
                "Content-Type: text/event-plain\nContent-Length: 17\n\n",
                StandardCharsets.UTF_8)))
                .isInstanceOf(DecoderException.class)
                .hasMessageContaining("ESL frame is longer than 16 bytes");
        channel.finishAndReleaseAll();
    }

    @Test
    void rejectsUnterminatedHeaderLineAboveLimit() {
        EmbeddedChannel channel = new EmbeddedChannel(new EslFrameDecoder(16, 1024));

        try {
            assertThatThrownBy(() -> channel.writeInbound(Unpooled.copiedBuffer(
                    "Content-Type: text/event-plain", StandardCharsets.UTF_8)))
                    .isInstanceOf(TooLongFrameException.class)
                    .hasMessageContaining("ESL header line is longer than 16 bytes");
        }
        finally {
            try {
                channel.finishAndReleaseAll();
            }
            catch (TooLongFrameException ignored) {
                // Netty may surface the decoder failure again while closing the embedded channel.
            }
        }
    }
}
