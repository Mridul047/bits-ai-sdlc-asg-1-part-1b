# Research: Strava Activity Insights Generator

**Date**: 2026-11-10

## R1: Spring Boot 4 Starter Names

**Decision**: Use `spring-boot-starter-webmvc` and
`spring-boot-starter-webmvc-test`.

**Rationale**: Spring Boot 4 renamed `spring-boot-starter-web` to
`spring-boot-starter-webmvc`. The old name is deprecated. Verified
against the official Spring Boot 4.1.2 reference docs. Per-technology
test starters (`spring-boot-starter-webmvc-test`) are the Boot 4
convention.

**Alternatives considered**: `spring-boot-starter-web` (deprecated,
Boot 3 era).

**Verified starters**:

| Starter | Group | Purpose |
| ------- | ----- | ------- |
| `spring-boot-starter-webmvc` | org.springframework.boot | Tomcat + Spring MVC |
| `spring-boot-starter-webmvc-test` | org.springframework.boot | MockMvc + JUnit 5 + Mockito |
| `spring-ai-starter-model-bedrock-converse` | org.springframework.ai | AWS Bedrock Converse |
| `spring-ai-bom` 2.0.1 | org.springframework.ai | Spring AI dependency management |

## R2: Spring AI 2 ChatClient API

**Decision**: Use `ChatClient.Builder` injection with the fluent API:
`.prompt().system().user().call().entity(Class)`.

**Rationale**: Verified against Spring AI 2.x reference documentation.
`ChatClient.Builder` is auto-configured and prototype-scoped. The
`.entity()` method maps the model's response directly to a Java
record, which provides built-in structured output support.

**Key API patterns**:

```java
// Injection
public MyService(ChatClient.Builder builder) {
    this.chatClient = builder.build();
}

// Structured output call
ModelOutput result = chatClient.prompt()
    .system(systemPromptResource)
    .user(userMessage)
    .call()
    .entity(ModelOutput.class);
```

**Template variables**: Use `{variableName}` syntax in prompt text,
filled via `.param("variableName", value)` on the system or user spec.

**Alternatives considered**: Direct `ChatModel` API (lower-level,
requires manual output parsing).

## R3: AWS Bedrock Converse Configuration

**Decision**: Configure via `spring.ai.bedrock.*` properties with
AWS credentials from environment variables.

**Rationale**: The `spring-ai-starter-model-bedrock-converse`
auto-configures the Bedrock client. AWS credentials map to the
constitution's "API key from environment variable" principle.

**Configuration properties**:

| Property | Value | Source |
| -------- | ----- | ------ |
| `spring.ai.bedrock.aws.region` | `${AWS_REGION}` | Env var |
| `spring.ai.bedrock.aws.access-key` | `${AWS_ACCESS_KEY_ID}` | Env var |
| `spring.ai.bedrock.aws.secret-key` | `${AWS_SECRET_ACCESS_KEY}` | Env var |
| `spring.ai.bedrock.aws.timeout` | `30s` | User requirement |
| `spring.ai.bedrock.converse.chat.model` | `${BEDROCK_MODEL_ID}` | Env var |
| `spring.ai.bedrock.converse.chat.temperature` | `0.2` | Spec (low temp) |
| `spring.ai.bedrock.converse.chat.max-tokens` | `2000` | Sufficient for 5 insights |

**Note**: "API key" in the constitution maps to AWS credentials
(access-key + secret-key + region). The startup validation checks
for `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, and
`BEDROCK_MODEL_ID`.

## R4: Jackson 3 (tools.jackson)

**Decision**: Use Jackson 3 auto-configured by Spring Boot 4. Apply
`SNAKE_CASE` naming strategy globally.

**Rationale**: Spring Boot 4 ships with Jackson 3 (`tools.jackson.*`
packages). The `com.fasterxml.jackson` packages (Jackson 2) MUST NOT
be imported. Global snake_case naming aligns with the JSON contract
(e.g., `sport_type`, `moving_time`, `evidence_activity_ids`).

**Configuration**:

```yaml
spring:
  jackson:
    property-naming-strategy: SNAKE_CASE
```

**Alternatives considered**: Per-record `@JsonNaming` annotations
(more verbose, same result).

## R5: Prompt Strategy

**Decision**: System prompt in `src/main/resources/prompts/` loaded
as a classpath resource. User message assembled programmatically with
facts, activities, and question in clearly delimited blocks.

**Rationale**: Constitution VIII requires prompts in a `prompts/`
directory. Spring AI loads resources from classpath. The system prompt
defines the closed metric list, refusal rules, and untrusted-data
instructions. The user message provides the data.

**Prompt structure**:
- System message: rules, schema, refusal criteria (loaded from file)
- User message: pre-computed facts JSON + sanitised activities in
  `=== UNTRUSTED DATA START/END ===` delimiters + question in
  `=== UNTRUSTED QUESTION START/END ===` delimiters

## R6: Refusal Handling

**Decision**: The model decides between OK and REFUSED status based
on refusal rules in the system prompt.

**Rationale**: The user's implementation direction states "The model
decides between OK and REFUSED." Deterministic detection of "unrelated
to the five metrics" is impractical. The system prompt encodes clear
refusal criteria. INSUFFICIENT_DATA remains deterministic (checked
before any model call).

**Spec note**: This refines spec assumption A-004 which stated
"REFUSED detection for explicit categories is implemented
deterministically." The model-based approach is more robust for the
catch-all category. The system prompt makes refusal rules explicit.

## R7: Harness Technology

**Decision**: Node.js (ESM) runner in `.harness/`.

**Rationale**: JSON-native, good HTTP client (fetch), mature JSON
Schema validation (ajv), independent from the Java build. The harness
is produced in a separate step before implementation, reviewed, and
hash-locked.

**Alternatives considered**: Python (viable, slightly more setup for
JSON Schema validation), Java (couples harness to application build).

## R8: Endpoint Path

**Decision**: `POST /insights` (not `/api/insights`).

**Rationale**: The user's implementation direction specifies
`POST /insights`. This overrides spec assumption A-001 which proposed
`/api/insights`. The endpoint path is an implementation detail refined
at plan time.
