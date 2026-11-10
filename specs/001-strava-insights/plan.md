# Implementation Plan: Strava Activity Insights Generator

**Branch**: `001-strava-insights` | **Date**: 2026-11-10 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/001-strava-insights/spec.md`

## Summary

Build a single-endpoint Spring Boot 4.1.2 service that generates
structured, factually verifiable insights about Strava activities using
AWS Bedrock (via Spring AI 2.0.1) for interpretive wording. The service
loads a JSON activity file from a configured directory, validates and
sanitises it, computes deterministic statistics, calls the model for
natural-language insight text, validates the model output against the
computed facts, and returns a structured response. All factual claims in
insights are machine-checkable against the input data.

The endpoint is `POST /insights`. The model decides between OK (with
insights) and REFUSED (for inappropriate questions). INSUFFICIENT_DATA
is determined deterministically before any model call.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**:
- Spring Boot 4.1.2 (`spring-boot-starter-webmvc`)
- Spring AI 2.0.1 (`spring-ai-starter-model-bedrock-converse`)
- Jackson 3 (`tools.jackson.*`, auto-configured by Spring Boot 4)

**Storage**: Filesystem only (JSON files in configured directory,
property `insights.source-dir`)

**Testing**: JUnit 5, Mockito, `spring-boot-starter-webmvc-test`
(per-technology test starter for Spring Boot 4)

**Target Platform**: JVM / any OS with Java 21

**Project Type**: Web service (single endpoint, no database, no UI)

**Performance Goals**: N/A (academic exercise; model latency dominates
end-to-end response time)

**Constraints**: 30s model timeout, max 200 activities per file,
temperature 0.2, file name regex
`^[A-Za-z0-9._-]{1,100}\.json$`

**Scale/Scope**: Single user, single endpoint, stateless

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1.*

| # | Principle | Status | Verification |
|---|-----------|--------|--------------|
| I | Simplicity | PASS | One endpoint, no DB/UI/streaming/RAG/memory/tools |
| II | Deterministic Core | PASS | FileActivitySource, ActivitySanitizer, FactsCalculator, INSUFFICIENT_DATA check are pure deterministic code. Model only produces wording. Model output validated before return. |
| III | Data Minimisation | PASS | ActivitySanitizer keeps only allow-listed fields. Location data never enters prompt, log, or response. |
| IV | Untrusted Input | PASS | Activity names and question passed in delimited blocks. System prompt instructs model to treat data blocks as untrusted. |
| V | Safe File Access | PASS | Regex validation + path normalisation + containment check against base directory. |
| VI | Secrets | PASS | AWS credentials from environment variables only. Never logged, returned, or committed. Live data folder gitignored. |
| VII | Failure Separation | PASS | MODEL_ERROR (502), MODEL_TIMEOUT (504), MODEL_OUTPUT_INVALID (502) are distinct error codes. |
| VIII | Reproducibility | PASS | Model ID, temperature, timeout in config. System prompt in `src/main/resources/prompts/`. |
| IX | Tests | PASS | All unit tests mock the model. No real model calls. No secrets in committed files. |
| X | Evaluation Harness | PASS | `.harness/` with runner, dataset, schema, evaluators. Produced and hash-locked before implementation. |
| XI | Agent Restrictions | PASS | Plan declares harness files immutable after lock. Implementation must not modify them. |
| XII | Traceability | PASS | Tests and eval cases tagged with requirement IDs (INV-xxx, GR-xxx, FR-xxx). |
| XIII | Change Control | PASS | Spec changes precede code changes. Threshold locked in spec. |

No violations. Gate passed.

## Project Structure

### Documentation (this feature)

```text
specs/001-strava-insights/
├── plan.md              # This file
├── research.md          # Phase 0: technology research
├── data-model.md        # Phase 1: entity definitions
├── quickstart.md        # Phase 1: validation guide
├── contracts/
│   └── api.md           # Phase 1: API contract
└── tasks.md             # Phase 2: task breakdown (via /speckit-tasks)
```

### Source Code (repository root)

```text
pom.xml
src/main/java/in/mycodethesaurus/athlenz_strava_agent/
├── AthlenzStravaAgentApplication.java
├── config/
│   └── InsightsProperties.java          # @ConfigurationProperties for insights.source-dir
├── controller/
│   └── InsightsController.java          # POST /insights endpoint
├── dto/
│   ├── Activity.java                    # Record: raw activity from JSON file
│   ├── ComputedFacts.java               # Record: deterministic aggregates
│   ├── ErrorResponse.java               # Record: error response body
│   ├── InsightItem.java                 # Record: single insight
│   ├── InsightsRequest.java             # Record: request body
│   ├── InsightsResponse.java            # Record: success response body
│   ├── Metadata.java                    # Record: response metadata
│   ├── ModelInsightOutput.java          # Record: structured model output
│   └── SanitizedActivity.java           # Record: allow-listed activity
├── exception/
│   ├── ErrorCode.java                   # Enum: 7 error codes with HTTP status
│   ├── GlobalExceptionHandler.java      # @RestControllerAdvice
│   └── InsightsException.java           # Runtime exception carrying ErrorCode
└── service/
    ├── ActivitySanitizer.java           # Strips to allow-list, truncates name
    ├── FactsCalculator.java             # Count, totals, longest, dominant sport
    ├── FileActivitySource.java          # Path validation, JSON parsing, schema checks
    ├── InsightsModelClient.java         # ChatClient call, retry, error mapping
    ├── InsightsService.java             # Orchestrator: validate → load → sanitise → compute → model → validate → respond
    └── OutputValidator.java             # Schema bounds, ID existence, value match

src/main/resources/
├── application.yaml
└── prompts/
    └── insights-system.txt              # System prompt (rules, schema, refusal criteria)

src/test/java/in/mycodethesaurus/athlenz_strava_agent/
├── AthlenzStravaAgentApplicationTests.java
├── controller/
│   └── InsightsControllerTest.java      # MockMvc integration tests
└── service/
    ├── ActivitySanitizerTest.java        # Allow-list enforcement
    ├── FactsCalculatorTest.java          # Rounding, edge cases
    ├── FileActivitySourceTest.java       # Path traversal, schema validation
    ├── InsightsModelClientPrivacyTest.java  # INV-007: no location in prompt
    ├── InsightsServiceTest.java          # Flow orchestration, INSUFFICIENT_DATA
    └── OutputValidatorTest.java          # Value matching, ID checks

src/test/resources/fixtures/
├── five-activities.json                  # Happy path (5 activities, known values)
├── two-activities.json                   # INSUFFICIENT_DATA
├── three-activities.json                 # Boundary (exactly 3)
├── max-200-activities.json               # Upper bound
├── duplicate-ids.json                    # INVALID_SOURCE_CONTENT
├── missing-fields.json                   # INVALID_SOURCE_CONTENT
├── negative-distance.json                # INVALID_SOURCE_CONTENT
├── location-fields.json                  # INV-007 test (has lat/lng to strip)
└── injection-names.json                  # GR-001 test (prompt injection in names)
```

### Evaluation Harness (produced separately, hash-locked)

```text
.harness/
├── runner.mjs                            # Entry point: hash check → start app → run cases → score → report
├── schema/
│   └── insights-response.schema.json     # JSON Schema for response validation (Layer 1)
├── evaluators/
│   ├── structural.mjs                    # INV-001, INV-006 checks
│   ├── invariant.mjs                     # INV-002 to INV-005 checks
│   ├── guardrail.mjs                     # GR-001 to GR-005 observable checks
│   └── semantic.mjs                      # Ground-truth fact comparison
├── thresholds.json                       # S, I, Q weights and pass criteria
├── self-tests/                           # Validates evaluators against known inputs
│   └── *.mjs
└── hashlock.json                         # SHA-256 of all harness files

evals/
├── dataset.json                          # >= 16 test cases with requirement IDs
└── fixtures/
    └── *.json                            # Synthetic activity files for eval cases

evidence/ (gitignored)
└── eval-results.json                     # Per-case results, traceability matrix, verdict
```

**Structure Decision**: Standard Spring Boot layered architecture
(controller → service → single-purpose components) with Java records
for all data transfer. The evaluation harness is an independent
Node.js (ESM) runner, decoupled from the Java build.

## Component Flow

```
POST /insights
  │
  ▼
InsightsController
  │ validate request (source regex, question length)
  ▼
InsightsService (orchestrator)
  │
  ├─→ FileActivitySource
  │     validate file name → resolve path → read JSON → parse → validate schema → reject duplicates
  │     returns: List<Activity>
  │
  ├─→ ActivitySanitizer
  │     strip non-allow-listed fields, truncate name
  │     returns: List<SanitizedActivity>
  │
  ├─→ FactsCalculator
  │     count, totalDistanceKm, totalMovingTimeMin, longestActivity, dominantSport
  │     returns: ComputedFacts
  │
  ├── if activityCount < 3 → return INSUFFICIENT_DATA (no model call)
  │
  ├─→ InsightsModelClient
  │     build prompt: system (from file) + user (facts + activities + question in delimited blocks)
  │     call ChatClient.entity(ModelInsightOutput.class) with temp=0.2, timeout=30s
  │     on provider failure → throw MODEL_ERROR / MODEL_TIMEOUT
  │     returns: ModelInsightOutput
  │
  ├─→ OutputValidator
  │     check schema bounds (field presence, types, string lengths, insight count)
  │     check all evidence_activity_ids exist in input
  │     check each structured value matches ComputedFacts within 0.1 rounding
  │     check metric-specific rules (LONGEST cites max-distance activity, DOMINANT names top sport)
  │     on failure → retry model once with validation errors appended
  │     if retry fails → throw MODEL_OUTPUT_INVALID
  │     returns: validated ModelInsightOutput
  │
  └─→ assemble InsightsResponse (status, summary, insights, metadata)
      return HTTP 200

GlobalExceptionHandler
  catches InsightsException → maps ErrorCode to HTTP status + ErrorResponse
  catches model-layer exceptions → MODEL_ERROR / MODEL_TIMEOUT
```

## Configuration

### application.yaml

```yaml
spring:
  application:
    name: athlenz-strava-agent
  jackson:
    property-naming-strategy: SNAKE_CASE
    deserialization:
      fail-on-unknown-properties: false
  ai:
    bedrock:
      aws:
        region: ${AWS_REGION}
        access-key: ${AWS_ACCESS_KEY_ID}
        secret-key: ${AWS_SECRET_ACCESS_KEY}
        timeout: 30s
      converse:
        chat:
          model: ${BEDROCK_MODEL_ID}
          temperature: 0.2
          max-tokens: 2000

insights:
  source-dir: ${INSIGHTS_SOURCE_DIR:./data}

logging:
  level:
    in.mycodethesaurus: INFO
    org.springframework.ai: WARN
```

### Startup Validation

`InsightsProperties` (annotated with `@ConfigurationProperties("insights")`)
validates `source-dir` is set. A `@PostConstruct` or
`ApplicationRunner` bean checks that `AWS_ACCESS_KEY_ID`,
`AWS_SECRET_ACCESS_KEY`, and `BEDROCK_MODEL_ID` environment variables
are present, failing startup with a clear message if any is missing.

## Build File Changes

The existing `pom.xml` is already correct for the core dependencies.
No changes needed to starter names or versions:

| Artifact | Version | Status |
| -------- | ------- | ------ |
| `spring-boot-starter-parent` | 4.1.2 | Already in pom.xml |
| `spring-boot-starter-webmvc` | (managed) | Already in pom.xml |
| `spring-ai-starter-model-bedrock-converse` | (managed) | Already in pom.xml |
| `spring-ai-bom` | 2.0.1 | Already in pom.xml |
| `spring-boot-starter-webmvc-test` | (managed) | Already in pom.xml |

## Spec Refinements at Plan Time

These implementation decisions refine spec assumptions:

| Spec Item | Original | Refined | Rationale |
| --------- | -------- | ------- | --------- |
| A-001 | `POST /api/insights` | `POST /insights` | User's explicit implementation direction |
| A-004 | Deterministic REFUSED detection | Model decides OK vs REFUSED via system prompt | "Unrelated to five metrics" is impractical to detect deterministically; model with explicit refusal rules is more robust |
| A-006 | evidence_activity_ids flexible | Model populates IDs; OutputValidator checks all exist and metric-specific rules (LONGEST, DOMINANT) | Lets the model pick relevant IDs; validator enforces invariants |

## Complexity Tracking

No constitution violations. This section is intentionally empty.
