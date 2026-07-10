# AG-UI Kotlin server starter

Kotlin sibling of [`server-starter/typescript`](../typescript) and
[`server-starter/python`](../python). It demonstrates the same idea those do: an
AG-UI server is **not** a framework or a base class — it is a recipe:

> **core types + [`EventEncoder`](../../../sdks/community/kotlin/library/encoder) + your web framework.**

There is no `EventFactory`, no `AbstractServerAgent`, no server-side event
verification. Kotlin `data class`es with named args stand in for
pydantic/TS constructors, and you build events directly from
`com.agui.core.types.*`.

Two flavours of the same recipe:

| Module    | Framework                    | Streaming primitive                          |
|-----------|------------------------------|----------------------------------------------|
| `:ktor`   | Ktor CIO (pure Kotlin)       | `call.respondBytesWriter { … }`              |
| `:spring` | Spring Boot 3 (servlet MVC)  | `StreamingResponseBody`                       |

Both emit the canonical SSE wire form `data: {json}\n\n` produced by
`EventEncoder.encode(event)`, identical to the Python and TypeScript starters.

## Prerequisites: publish the SDK to mavenLocal

These integrations consume `com.ag-ui.community:kotlin-core:0.4.1` and
`:kotlin-encoder:0.4.1` from your local Maven cache. Publish them first, from
`sdks/community/kotlin/library/`:

```bash
./gradlew :kotlin-core:publishToMavenLocal :kotlin-encoder:publishToMavenLocal
```

> If you have no Android SDK installed, `kotlin-core`'s Android target makes the
> aggregate task fail. Publish just the JVM + metadata variants instead — that is
> all a JVM consumer needs:
>
> ```bash
> ./gradlew :kotlin-core:publishJvmPublicationToMavenLocal \
>           :kotlin-core:publishKotlinMultiplatformPublicationToMavenLocal \
>           :kotlin-encoder:publishJvmPublicationToMavenLocal \
>           :kotlin-encoder:publishKotlinMultiplatformPublicationToMavenLocal \
>           -x dokkaGeneratePublicationHtml -x :kotlin-core:extractReleaseAnnotations
> ```

## Run

From this directory (`integrations/server-starter/kotlin/`):

```bash
./gradlew :ktor:run        # Ktor    on http://localhost:8000
./gradlew :spring:bootRun  # Spring  on http://localhost:8080
```

Then POST a `RunAgentInput`:

```bash
curl -N -X POST http://localhost:8000/agents/my-agent/run \
  -H 'Content-Type: application/json' \
  -H 'Accept: text/event-stream' \
  -d '{"threadId":"t1","runId":"r1","messages":[],"tools":[],"context":[],"state":{}}'
```

You get the echo lifecycle: `RUN_STARTED` → (`CUSTOM AGENT_SELECTED`, Spring only)
→ `TEXT_MESSAGE_START` → `TEXT_MESSAGE_CONTENT("Hello world!")` →
`TEXT_MESSAGE_END` → `RUN_FINISHED`.

## Test

```bash
./gradlew :ktor:build :spring:build   # compiles + runs both test suites
```

- `:ktor` uses `testApplication`; `:spring` uses `@SpringBootTest(RANDOM_PORT)` +
  the JDK HTTP client. Both decode the SSE body back with `AgUiJson` and assert
  the event sequence, and assert a malformed body is a 4xx.

## Notes for implementers

### `StreamingResponseBody`, not `SseEmitter` (Spring)

The Spring controller returns `StreamingResponseBody` and writes
`encoder.encode(event)` raw. **Do not** use Spring's `SseEmitter` /
`ServerSentEvent` here: they add their *own* `data:` framing, which would
double-frame on top of what `EventEncoder.encode()` already produces.
`StreamingResponseBody` + `encoder.encode()` is the raw-streaming pairing that
mirrors FastAPI's `StreamingResponse` and keeps the wire identical across all
three starters. (If you ever did use `SseEmitter`, you would pass it only
`AgUiJson.encodeToString(event)` and let Spring frame — but we deliberately do
not.)

### kotlinx-serialization, not Jackson (Spring)

`kotlin-core` types are `kotlinx-serialization` `@Serializable`, so the Spring
controller takes `@RequestBody` as a raw `String` and decodes it with `AgUiJson`
rather than Spring's Jackson. `spring-boot-starter-web` still pulls Jackson in;
we simply do not use it on the AG-UI wire. Note also that Spring Boot's BOM pins
kotlinx-serialization to an older version — `build.gradle.kts` overrides
`kotlin-serialization.version` to match `kotlin-core` (1.8.1).

### The agent seam (Beacon / Koog)

The `:spring` module is written in Kotlin (not Java) and shaped for
[Beacon](../../../) (Kotlin + Spring Boot + Koog). The single seam where a real
agent replaces the echo is the `Agent` functional interface (`EchoAgent`,
wired as a `@Bean`): `run(input: RunAgentInput): Sequence<BaseEvent>`. Beacon
translates a Koog `AIAgent`'s `StreamFrame`s into AG-UI events there. The echo
also emits a `CustomEvent(name = "AGENT_SELECTED", …)` to exercise the CUSTOM
path unimarket-ui / Beacon route on. `RunAgentInput.threadId` is required
(non-nullable) in kotlin-core.
```
