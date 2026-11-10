# Quickstart Validation Guide

**Date**: 2026-11-10

## Prerequisites

- Java 21
- Maven 3.9+
- AWS credentials with Bedrock access (for live model calls)
- A JSON activity fixture file (see [contracts/api.md](contracts/api.md)
  for the source file format)

## Configuration

Set required environment variables:

```bash
export AWS_ACCESS_KEY_ID=<your-key>
export AWS_SECRET_ACCESS_KEY=<your-secret>
export AWS_REGION=us-east-1
export BEDROCK_MODEL_ID=us.anthropic.claude-sonnet-4-20250514-v1:0
```

Create or use the default source directory:

```bash
mkdir -p data/
cp src/test/resources/fixtures/five-activities.json data/
```

## Build

```bash
mvn clean package -DskipTests
```

## Run

```bash
java -jar target/athlenz-strava-agent-0.0.1-SNAPSHOT.jar \
  --insights.source-dir=./data
```

The service starts on port 8080 by default.

## Validate: Happy Path (POST /insights)

```bash
curl -s -X POST http://localhost:8080/insights \
  -H "Content-Type: application/json" \
  -d '{"source": "five-activities.json"}' | jq .
```

**Expected**: HTTP 200, `status: "OK"`, 5 insights (general overview),
`metadata.activities_analysed` matches file, all
`evidence_activity_ids` reference valid IDs.

## Validate: With Question

```bash
curl -s -X POST http://localhost:8080/insights \
  -H "Content-Type: application/json" \
  -d '{"source": "five-activities.json", "question": "What is my longest run?"}' | jq .
```

**Expected**: HTTP 200, `status: "OK"`, 1-5 insights, at least one
`LONGEST_ACTIVITY_DISTANCE_KM` insight citing the correct activity.

## Validate: Insufficient Data

```bash
curl -s -X POST http://localhost:8080/insights \
  -H "Content-Type: application/json" \
  -d '{"source": "two-activities.json"}' | jq .
```

**Expected**: HTTP 200, `status: "INSUFFICIENT_DATA"`,
`insights: []`, no model call made.

## Validate: Refused Question

```bash
curl -s -X POST http://localhost:8080/insights \
  -H "Content-Type: application/json" \
  -d '{"source": "five-activities.json", "question": "Do I have a knee injury?"}' | jq .
```

**Expected**: HTTP 200, `status: "REFUSED"`, `insights: []`.

## Validate: Invalid Source Name

```bash
curl -s -X POST http://localhost:8080/insights \
  -H "Content-Type: application/json" \
  -d '{"source": "../etc/passwd"}' | jq .
```

**Expected**: HTTP 400, `code: "INVALID_SOURCE_NAME"`.

## Validate: Source Not Found

```bash
curl -s -X POST http://localhost:8080/insights \
  -H "Content-Type: application/json" \
  -d '{"source": "nonexistent.json"}' | jq .
```

**Expected**: HTTP 404, `code: "SOURCE_NOT_FOUND"`.

## Run Unit Tests

```bash
mvn test
```

All tests mock the model — no AWS credentials needed.

## Run Evaluation Harness

```bash
cd .harness
node runner.mjs --mode development
```

Development mode runs each case once. Final evaluation:

```bash
node runner.mjs --mode final
```

Final mode runs each case 3 times. Results are written to
`evidence/eval-results.json`.

## Startup Validation

Missing credentials cause immediate startup failure:

```bash
unset AWS_ACCESS_KEY_ID
java -jar target/athlenz-strava-agent-0.0.1-SNAPSHOT.jar \
  --insights.source-dir=./data
# Expected: startup fails with clear error message
```
