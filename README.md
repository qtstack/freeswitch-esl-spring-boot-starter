# FreeSWITCH ESL Spring Boot Starter

[![CI](https://github.com/qtstack/freeswitch-esl-spring-boot-starter/actions/workflows/ci.yml/badge.svg)](https://github.com/qtstack/freeswitch-esl-spring-boot-starter/actions/workflows/ci.yml)

`io.github.qtstack:freeswitch-esl-spring-boot-starter` is a Netty based FreeSWITCH Event Socket Layer
(ESL) client and Spring Boot starter. It supports inbound ESL connections, authentication,
automatic reconnection, plain event decoding, asynchronous commands, and annotation based event
routing.

## Requirements

* Java 25 or newer
* Spring Boot 4.x only; Boot 2.x and 3.x are intentionally unsupported
* Netty 4.2.19.Final, included transitively

The Spring Boot auto-configuration API is a `provided` dependency. Your application chooses its
Spring Boot 4.x patch version.

## Install

```xml
<dependency>
    <groupId>io.github.qtstack</groupId>
    <artifactId>freeswitch-esl-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Configuration

```yaml
freeswitch:
  esl:
    enabled: true
    auto-startup: true
    password: ${FREESWITCH_ESL_PASSWORD}
    events:
      - HEARTBEAT
      - CHANNEL_CREATE
      - CHANNEL_DESTROY
    custom-events:
      - callcenter::info
    command-timeout: 10s
    connect-timeout: 5s
    reconnect-delay: 5s
    worker-threads: 0
    event-threads: 0
    event-queue-capacity: 4096
    max-header-line-length: 8192
    max-frame-length: 1MB
    tcp-no-delay: true
    servers:
      - host: 127.0.0.1
        port: 8021
```

The starter is opt-in: set `freeswitch.esl.enabled=true` to create the client beans. No default
credential is assumed. Every server must have a password, either through the shared
`freeswitch.esl.password` or through its own `password` entry. Each server is identified as
`host:port` (IPv6 is written as `[address]:port`).

Thread counts and protocol limits set to `0` use sensible defaults. `event-queue-capacity` bounds
the events waiting for listener threads; when the queue is full, new events are dropped with a
warning so a slow listener cannot exhaust memory. `max-frame-length` limits an ESL message body.

`events` contains one FreeSWITCH event name per entry. Put `CUSTOM` subclasses in
`custom-events` instead of writing `CUSTOM callcenter::info` as a single event string. After
authentication, the example above is sent as one ESL command:

```text
event plain HEARTBEAT CHANNEL_CREATE CHANNEL_DESTROY CUSTOM callcenter::info
```

## Event Handling

Use `EslEventListener` for full control:

```java
@Component
class FreeSwitchEvents implements EslEventListener {
    @Override
    public void onEvent(String address, EslEvent event) {
        System.out.printf("%s -> %s%n", address, event.eventName());
    }
}
```

Or route events with an annotation:

```java
@Component
@EslEventName("HEARTBEAT")
class HeartbeatHandler implements EslEventHandler {
    @Override
    public void handle(String address, EslEvent event) {
        System.out.printf("FreeSWITCH at %s is alive%n", address);
    }
}
```

`EslEventName` accepts one or more FreeSWITCH event names. An empty value matches every event.
Custom `EslEventListener` beans and annotated `EslEventHandler` beans can coexist; each receives
the matching events independently. A handler exception is logged and does not prevent other
handlers from running.

### Custom Events

Route a CUSTOM event by its subclass:

```java
@Component
@EslEventName(subclass = "callcenter::info")
class CallcenterInfoHandler implements EslEventHandler {
    @Override
    public void handle(String address, EslEvent event) {
        System.out.printf("Callcenter info from %s: %s%n", address, event.header("Caller-Name"));
    }
}
```

Use `@EslEventName("CUSTOM")` to handle every CUSTOM subclass, and `EslEvent.eventSubclass()`
to inspect it. Handler annotations route events but do not create subscriptions; include the
event or subclass in `freeswitch.esl.events` or `freeswitch.esl.custom-events`.

## Commands

Inject `EslClient`:

```java
@Service
class EslService {
    private final EslClient client;

    EslService(EslClient client) {
        this.client = client;
    }

    CompletableFuture<String> uptime() {
        return client.sendApiCommand("127.0.0.1:8021", "status", "")
                .thenApply(response -> String.join("\n", response.bodyLines()));
    }
}
```

The API covers `api`, `bgapi`, `event`, custom event subscriptions, `noevents`, `filter`,
`sendevent`, `sendmsg`, `log`, and `nolog`. Commands are asynchronous and honor
`command-timeout`. If the connection closes, pending futures fail instead of blocking forever.
Commands, filters, and message builders reject embedded line breaks before writing to the socket.

## Project Layout

```text
src/main/java/io/github/qtstack/fsesl/          Public client API and configuration
src/main/java/io/github/qtstack/fsesl/transport ESL protocol objects and frame decoder
src/main/java/io/github/qtstack/fsesl/internal  Netty implementation, not public API
src/main/java/io/github/qtstack/fsesl/spring/boot Spring Boot 4.x auto-configuration
```

## Build

The build requires JDK 25.

```powershell
mvn verify
```

The build runs unit and local protocol integration tests, then creates binary, source, and Javadoc
artifacts. To publish to Sonatype Central:

```powershell
mvn -P release deploy
```

Configure your signing key and a `central` server entry in your local Maven settings first.

The optional external-server integration test is skipped by default and never contains a committed
host or password. Enable it with explicit properties:

```powershell
mvn test "-Dfreeswitch.esl.it.enabled=true" `
  "-Dfreeswitch.esl.it.host=<host>" `
  "-Dfreeswitch.esl.it.port=8021" `
  "-Dfreeswitch.esl.it.password=<password>"
```

## License

Apache License 2.0. See [LICENSE](LICENSE).
