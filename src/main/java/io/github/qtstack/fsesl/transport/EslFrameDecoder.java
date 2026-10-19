package io.github.qtstack.fsesl.transport;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.TooLongFrameException;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Decodes the text based ESL framing protocol:
 *
 * <pre>
 * Header-Name: value\r\n
 * ...
 * \r\n
 * body bytes...
 * </pre>
 *
 * A message without Content-Length ends at its blank header terminator. A message with
 * Content-Length continues for exactly that number of body bytes.
 */
public final class EslFrameDecoder extends ByteToMessageDecoder {

    private enum State {
        READ_HEADERS,
        READ_BODY
    }

    private final int maxHeaderLineLength;
    private final int maxFrameLength;
    private State state = State.READ_HEADERS;
    private EslMessage.Builder message = EslMessage.builder();
    private int remainingBodyBytes;

    public EslFrameDecoder(int maxHeaderLineLength, int maxFrameLength) {
        if (maxHeaderLineLength <= 0 || maxFrameLength <= 0) {
            throw new IllegalArgumentException("Decoder limits must be positive");
        }
        this.maxHeaderLineLength = maxHeaderLineLength;
        this.maxFrameLength = maxFrameLength;
    }

    @Override
    protected void decode(ChannelHandlerContext context, ByteBuf buffer, List<Object> output) {
        if (state == State.READ_HEADERS && !decodeHeaders(buffer, output)) {
            return;
        }
        if (state == State.READ_BODY) {
            decodeBody(buffer, output);
        }
    }

    private boolean decodeHeaders(ByteBuf buffer, List<Object> output) {
        while (buffer.isReadable()) {
            String line = readHeaderLine(buffer);
            if (line == null) {
                return false;
            }
            if (line.isBlank()) {
                if (!message.hasContentLength0()) {
                    emit(output);
                    return true;
                }
                remainingBodyBytes = message.contentLength0();
                if (remainingBodyBytes < 0) {
                    throw new TooLongFrameException("Negative Content-Length");
                }
                if (remainingBodyBytes > maxFrameLength) {
                    throw new TooLongFrameException("ESL frame is longer than " + maxFrameLength + " bytes");
                }
                state = State.READ_BODY;
                return true;
            }
            int separator = line.indexOf(':');
            if (separator <= 0 || line.length() <= separator + 1) {
                throw new IllegalStateException("Invalid ESL header line: " + line);
            }
            String name = line.substring(0, separator).trim();
            String value = line.substring(separator + 1).trim();
            message.header(name, value);
        }
        return false;
    }

    private void decodeBody(ByteBuf buffer, List<Object> output) {
        int readableBytes = buffer.readableBytes();
        if (readableBytes < remainingBodyBytes) {
            return;
        }
        ByteBuf body = buffer.readBytes(remainingBodyBytes);
        try {
            String text = remainingBodyBytes == 0 ? "" : body.toString(StandardCharsets.UTF_8);
            String[] lines = text.split("\n", -1);
            for (int index = 0; index < lines.length; index++) {
                String line = stripTrailingCr(lines[index]);
                boolean lastInputLine = index == lines.length - 1;
                if (lastInputLine && line.isEmpty()) {
                    continue;
                }
                message.bodyLine(line);
            }
            emit(output);
        }
        finally {
            body.release();
        }
    }

    private void emit(List<Object> output) {
        output.add(message.build());
        message = EslMessage.builder();
        state = State.READ_HEADERS;
        remainingBodyBytes = 0;
    }

    private String readHeaderLine(ByteBuf buffer) {
        int start = buffer.readerIndex();
        int index = buffer.indexOf(start, buffer.writerIndex(), (byte) '\n');
        if (index < 0) {
            if (buffer.readableBytes() > maxHeaderLineLength) {
                throw new TooLongFrameException(
                        "ESL header line is longer than " + maxHeaderLineLength + " bytes");
            }
            return null;
        }
        int length = index - start;
        if (length > maxHeaderLineLength) {
            throw new TooLongFrameException("ESL header line is longer than " + maxHeaderLineLength + " bytes");
        }
        buffer.readerIndex(index + 1);
        return stripTrailingCr(buffer.toString(start, length, StandardCharsets.UTF_8));
    }

    private static String stripTrailingCr(String line) {
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }
}
