# Data Model: Strava Activity Insights Generator

**Date**: 2026-11-10

All data carriers are Java records (immutable, no setters). Jackson 3
snake_case naming is applied globally. Package:
`in.mycodethesaurus.athlenz_strava_agent.dto`.

## Entities

### Activity (raw input from JSON file)

Deserialized directly from the source file. Jackson ignores unknown
properties (fields outside the allow-list are silently dropped).

| Field | Java type | JSON name | Constraint |
| ----- | --------- | --------- | ---------- |
| id | `long` | `id` | positive integer, unique across file |
| name | `String` | `name` | optional, may be null |
| sportType | `String` | `sport_type` | non-empty |
| distance | `double` | `distance` | >= 0, metres |
| movingTime | `long` | `moving_time` | >= 0, seconds |
| startDate | `String` | `start_date` | ISO-8601 UTC |

**Validation rules** (enforced by `FileActivitySource`):
- Array size: 1-200 objects
- Each object MUST have id, sportType, distance, movingTime, startDate
- id MUST be > 0
- distance MUST be >= 0
- movingTime MUST be >= 0
- sportType MUST be non-empty
- No duplicate IDs across the array
- Schema failure on ANY activity rejects the entire file (per
  clarification: strict validation)

### SanitizedActivity (allow-listed, immutable)

Produced by `ActivitySanitizer`. Identical fields to Activity but with
name truncated to 100 characters. This record is the ONLY activity
representation that ever enters a prompt, log, or response.

| Field | Java type | JSON name | Constraint |
| ----- | --------- | --------- | ---------- |
| id | `long` | `id` | from validated Activity |
| name | `String` | `name` | truncated to 100 chars, may be null |
| sportType | `String` | `sport_type` | from validated Activity |
| distance | `double` | `distance` | from validated Activity |
| movingTime | `long` | `moving_time` | from validated Activity |
| startDate | `String` | `start_date` | from validated Activity |

### ComputedFacts (deterministic aggregates)

Produced by `FactsCalculator` from the sanitized activity list.
All rounding uses `BigDecimal` with `HALF_UP` to 1 decimal place.

| Field | Java type | Description |
| ----- | --------- | ----------- |
| activityCount | `int` | Number of activities |
| totalDistanceKm | `double` | Sum of distances / 1000, 1dp |
| totalMovingTimeMin | `double` | Sum of moving_time / 60, 1dp |
| longestActivityDistanceKm | `double` | Max distance / 1000, 1dp |
| longestActivityId | `long` | ID of the activity with max distance |
| dominantSport | `String` | Sport with most activities |
| dominantSportActivityIds | `List<Long>` | IDs of activities matching dominant sport |
| allActivityIds | `List<Long>` | All activity IDs (for evidence) |

### InsightsRequest (HTTP request body)

| Field | Java type | JSON name | Constraint |
| ----- | --------- | --------- | ---------- |
| source | `String` | `source` | required, validated by regex |
| question | `String` | `question` | optional, max 300 chars |

### InsightsResponse (HTTP 200 response body)

| Field | Java type | JSON name | Constraint |
| ----- | --------- | --------- | ---------- |
| status | `String` | `status` | OK, INSUFFICIENT_DATA, REFUSED |
| summary | `String` | `summary` | 1-400 chars |
| insights | `List<InsightItem>` | `insights` | empty or 1-5 items |
| metadata | `Metadata` | `metadata` | always present |

### InsightItem

| Field | Java type | JSON name | Constraint |
| ----- | --------- | --------- | ---------- |
| metric | `String` | `metric` | one of 5 metric names |
| text | `String` | `text` | 1-300 chars |
| confidence | `String` | `confidence` | LOW, MEDIUM, HIGH |
| evidenceActivityIds | `List<Long>` | `evidence_activity_ids` | non-empty |
| value | `Object` | `value` | Number or String |
| unit | `String` | `unit` | count, km, min, sport |

### Metadata

| Field | Java type | JSON name | Constraint |
| ----- | --------- | --------- | ---------- |
| activitiesAnalysed | `int` | `activities_analysed` | activity count |
| source | `String` | `source` | file name from request |

### ModelInsightOutput (structured output from model)

What the model returns via `ChatClient.entity()`. The service
validates this against `ComputedFacts` before using it.

| Field | Java type | JSON name | Constraint |
| ----- | --------- | --------- | ---------- |
| status | `String` | `status` | OK or REFUSED |
| summary | `String` | `summary` | 1-400 chars |
| insights | `List<InsightItem>` | `insights` | 0-5 items |

### ErrorResponse (non-200 responses)

| Field | Java type | JSON name | Constraint |
| ----- | --------- | --------- | ---------- |
| code | `String` | `code` | error code enum value |
| message | `String` | `message` | human-readable, never includes key or file contents |

### ErrorCode (enum)

| Value | HTTP Status | Category |
| ----- | ----------- | -------- |
| INVALID_SOURCE_NAME | 400 | Client error |
| INVALID_QUESTION | 400 | Client error |
| SOURCE_NOT_FOUND | 404 | Client error |
| INVALID_SOURCE_CONTENT | 422 | Client error |
| MODEL_ERROR | 502 | Service failure |
| MODEL_TIMEOUT | 504 | Service failure |
| MODEL_OUTPUT_INVALID | 502 | Application failure |

## Relationships

```
InsightsRequest
  └─→ FileActivitySource → List<Activity>
       └─→ ActivitySanitizer → List<SanitizedActivity>
            └─→ FactsCalculator → ComputedFacts
                 └─→ InsightsModelClient → ModelInsightOutput
                      └─→ OutputValidator → InsightsResponse
```

## State Transitions

None. The service is stateless — each request is independent with
no persistent state.
