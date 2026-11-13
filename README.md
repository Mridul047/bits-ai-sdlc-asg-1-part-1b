# Athlenz Strava Insights Generator

A Spring Boot backend that turns a set of Strava activities into up to five structured,
machine-verifiable insights. A client sends the name of a JSON activities file and an
optional natural-language question to `POST /api/insights`. The service loads the file,
sanitises it against an allow-list, computes five metrics with plain deterministic code,
and calls a language model on AWS Bedrock only to word those pre-computed facts. The
model output is parsed and validated against the computed facts before it is returned, so
every numeric claim in a response traces back to a value the service calculated itself.
The project was built spec-first with GitHub Spec Kit and Claude, and is gated by a
hash-locked Node evaluation harness. Current status: harness final-mode PASS
(aggregate 0.983), human decision **ACCEPT**.

## Architecture

Request flow for `POST /api/insights`:

```
load file  ->  sanitise (allow-list)  ->  compute facts  ->  model wording (Bedrock)  ->  validate  ->  response
```

1. **Load** — `FileActivitySource` reads a bare file name from the configured source
   directory. Path separators, `..`, and names outside `^[A-Za-z0-9._-]{1,100}\.json$`
   are rejected before any I/O. An array of 0–200 activities is accepted; more than 200 is
   rejected.
2. **Sanitise** — `ActivitySanitizer` keeps only an allow-list of fields
   (`id`, `name`, `sport_type`, `distance`, `moving_time`, `start_date`) and truncates long
   names. Location data and every other field are dropped and never sent to the model.
3. **Compute facts** — `FactsCalculator` deterministically computes the five metrics.
4. **Model wording** — if at least 3 activities are present, `InsightsModelClient` asks
   the Bedrock model to produce interpretive wording from the facts only. Fewer than 3
   activities short-circuits to `INSUFFICIENT_DATA` with no model call.
5. **Validate** — `OutputValidator` checks the model output against the computed facts;
   one retry with feedback is attempted before the request fails with `MODEL_OUTPUT_INVALID`.

The five metrics (also the only metric names the model may emit):

| Metric | Unit |
|---|---|
| `ACTIVITY_COUNT` | count |
| `TOTAL_DISTANCE_KM` | km |
| `TOTAL_MOVING_TIME_MIN` | min |
| `LONGEST_ACTIVITY_DISTANCE_KM` | km |
| `DOMINANT_SPORT` | sport |

Response `status` values: `OK`, `INSUFFICIENT_DATA`, `REFUSED`. A successful HTTP 200
response carries `status`, `summary`, `insights[]`, and `metadata` (`activities_analysed`,
`source`). Error responses carry `code` and `message`.

Error codes and HTTP status (from `exception/ErrorCode.java`):

| Code | HTTP |
|---|---|
| `INVALID_SOURCE_NAME` | 400 |
| `INVALID_QUESTION` | 400 |
| `SOURCE_NOT_FOUND` | 404 |
| `INVALID_SOURCE_CONTENT` | 422 |
| `MODEL_ERROR` | 502 |
| `MODEL_TIMEOUT` | 504 |
| `MODEL_OUTPUT_INVALID` | 502 |

## Tech stack and prerequisites

- **Java 21** (application `java.version` is 21).
- **Spring Boot 4.1.2** with **Spring AI 2.0.1** (`spring-ai-starter-model-bedrock-converse`).
- **Maven** (the Spring Boot parent and wrapper conventions; Maven 3.9+).
- **Node.js** for the evaluation harness (the recorded runs used Node v24.14.1; the harness
  depends on `ajv` for JSON Schema validation).
- **AWS Bedrock** access with a model that supports the Converse API, plus credentials and a
  region.

## Configuration

Secrets come only from the environment. A local `.env` file is git-ignored (see `.gitignore`);
never commit real credentials. Set these variables (placeholders shown):

| Variable | Purpose |
|---|---|
| `AWS_ACCESS_KEY_ID` | AWS access key for Bedrock |
| `AWS_SECRET_ACCESS_KEY` | AWS secret key for Bedrock |
| `AWS_REGION` | AWS region, e.g. `us-east-1` |
| `BEDROCK_MODEL_ID` | Bedrock model / inference profile id |
| `AWS_SESSION_TOKEN` | Optional, for temporary credentials |
| `INSIGHTS_SOURCE_DIR` | Optional, directory the service reads activity files from (default `./data`) |

Application properties (`src/main/resources/application.yaml`):

- `spring.ai.bedrock.aws.region` <- `AWS_REGION`
- `spring.ai.bedrock.converse.chat.model` <- `BEDROCK_MODEL_ID`, `temperature: 0.2`,
  `max-tokens: 2000`, request `timeout: 30s`
- `insights.source-dir` <- `INSIGHTS_SOURCE_DIR` (default `./data`)

The service reads activity files from `insights.source-dir`. In normal use this is `./data`
(the committed sample `data/my-activities.json` lives there). The evaluation harness starts
the application pointed at `evals/fixtures/` instead.

Load the environment before running.

PowerShell:

```powershell
$env:AWS_ACCESS_KEY_ID     = "<your-key>"
$env:AWS_SECRET_ACCESS_KEY = "<your-secret>"
$env:AWS_REGION            = "us-east-1"
$env:BEDROCK_MODEL_ID      = "<your-model-id>"
```

bash:

```bash
export AWS_ACCESS_KEY_ID=<your-key>
export AWS_SECRET_ACCESS_KEY=<your-secret>
export AWS_REGION=us-east-1
export BEDROCK_MODEL_ID=<your-model-id>
```

If you keep the values in a git-ignored `.env`, load it first, for example
`set -a; . ./.env; set +a` in bash.

## Build, test and run

```bash
mvn test                      # unit tests; model is mocked, no AWS needed
mvn clean package             # build the jar
java -jar target/athlenz-strava-agent-0.0.1-SNAPSHOT.jar
# or
mvn spring-boot:run
```

The service listens on port 8080 (Spring Boot default; no override is set).

Example request (bash):

```bash
curl -s -X POST http://localhost:8080/api/insights \
  -H "Content-Type: application/json" \
  -d '{"source": "my-activities.json", "question": "What is my longest activity?"}'
```

PowerShell:

```powershell
curl.exe -s -X POST http://localhost:8080/api/insights `
  -H "Content-Type: application/json" `
  -d '{\"source\": \"my-activities.json\", \"question\": \"What is my longest activity?\"}'
```

The request body has two fields: `source` (required JSON file name) and `question`
(optional, max 300 characters). Example response (HTTP 200, values illustrative):

```json
{
  "status": "OK",
  "summary": "Overview of your logged activities.",
  "insights": [
    {
      "metric": "LONGEST_ACTIVITY_DISTANCE_KM",
      "text": "Your longest activity covered 30.0 km.",
      "confidence": "HIGH",
      "evidence_activity_ids": [2],
      "value": 30.0,
      "unit": "km"
    }
  ],
  "metadata": {
    "activities_analysed": 5,
    "source": "my-activities.json"
  }
}
```

A ready-made Postman collection is in
[`src/main/resources/postman/athlenz-strava-insights.postman_collection.json`](src/main/resources/postman/athlenz-strava-insights.postman_collection.json).

> Note: the older plan and quickstart documents under `specs/` refer to `/insights`. The
> implemented endpoint is `/api/insights` (`InsightsController` is mapped at `/api`). This is
> a tracked documentation mismatch (see Known limitations).

## Evaluation harness

The harness in `.harness/` runs the live service against `evals/dataset.json` and scores each
response in three layers:

- **Layer 1 — structural (S)**: JSON Schema conformance of success and error responses
  (`ajv` against `.harness/schema/`).
- **Layer 2 — invariant/property + guardrail (I)**: deterministic rules (INV-001…INV-006)
  and safety guardrails (GR-001…GR-005), such as values matching recomputed facts, evidence
  id rules, and refusal/no-leak checks.
- **Layer 3 — semantic/quality (Q)**: ground-truth comparison of a response against the
  case's `expected_facts`. No LLM judge is used; Q is forced to 0 when S or I fails.

Scoring and PASS rule (`.harness/thresholds.json`, mirrored in the spec): weights
`S=0.2, I=0.4, Q=0.4`, `Aggregate = 0.2·S + 0.4·I + 0.4·Q` averaged over included runs. A
run **PASSes** only when `Aggregate >= 0.90`, `S = 1.0`, and every guardrail check passes.
A run is **INCONCLUSIVE** if more than 20% of cases are excluded (`MODEL_ERROR`,
`MODEL_TIMEOUT`). Development mode runs each case once; final mode runs each case 3 times.

Run it:

```bash
cd .harness
npm install                       # installs ajv
node self-tests/evaluator-tests.mjs   # offline evaluator self-tests
node runner.mjs --mode development    # 1 repetition per case
node runner.mjs --mode final          # 3 repetitions per case
```

The runner needs a live Bedrock endpoint and the environment variables above; it starts the
built jar, points its source directory at `evals/fixtures/`, and **overwrites**
`evidence/eval-results.json` with the latest results.

**Hash lock.** `generate-hashlock.mjs` records SHA-256 hashes of the harness artifacts
(dataset, fixtures, schema, evaluators, thresholds, runner) in `.harness/hashlock.json`.
`runner.mjs` verifies these hashes first and stops on any mismatch. Regenerating the lock
means re-approving the harness, which per the project constitution requires human sign-off
(locked harness files must not be changed to obtain a pass).

**Dataset.** `evals/dataset.json` holds 16 cases (TC-001…TC-016) spanning representative
inputs, boundary cases (2 and 3 activities, empty array, over-length question), ambiguous
questions, missing/malformed input, and adversarial/guardrail cases (path traversal,
duplicate ids, prompt injection in activity names).

## Results

| Evaluation | Mode | Runs | Verdict | S | I | Q | Aggregate |
|---|---|---|---|---|---|---|---|
| 1 (initial) | development | 16 | FAIL | 1.000 | 0.875 | 0.792 | 0.867 |
| 2 (after repair) | development | 16 | PASS | 1.000 | 1.000 | 0.958 | 0.983 |
| 3 (final) | final | 48 | PASS | 1.000 | 1.000 | 0.958 | 0.983 |

Evaluation 1 failed on two harness-driven findings. First, the model wrote calendar dates
and a derived average into insight text (TC-001, TC-015), tripping INV-005; the fix added a
"Numbers and Dates in Text" section to the system prompt forbidding dates and any derived,
averaged, or converted number, allowing only the five fact values or a cited activity id.
Second, an empty activities array returned HTTP 422 instead of the specified
`INSUFFICIENT_DATA` (TC-006); the fix made an empty array schema-valid in `FileActivitySource`
and had `FactsCalculator` return zeroed facts for an empty list, after a first attempt caused
an HTTP 500 regression that the harness caught. After these repairs the development run passed
at 0.983, and the final-mode run (3 repetitions, 48 runs) reproduced the same scores with no
cross-repetition variance. Full detail is in
[`evidence/harness-log.md`](evidence/harness-log.md); per-run summaries are in
[`evidence/initial-generation/summary.md`](evidence/initial-generation/summary.md) and
[`evidence/final-generation/summary.md`](evidence/final-generation/summary.md).

## Repository layout

```
.
├── pom.xml                       # Maven build (Spring Boot 4 / Spring AI 2, Java 21)
├── README.md
├── data/                         # Activity source files read at runtime (sample included)
│   └── my-activities.json        # Sample Strava-style export
├── src/
│   ├── main/java/.../athlenz_strava_agent/
│   │   ├── controller/           # InsightsController (POST /api/insights)
│   │   ├── service/              # file source, sanitiser, facts, model client, validator
│   │   ├── dto/                  # request/response/records
│   │   ├── config/               # InsightsProperties, StartupValidator
│   │   └── exception/            # ErrorCode and handlers
│   └── main/resources/
│       ├── application.yaml       # config and env bindings
│       ├── prompts/               # version-controlled system prompt
│       └── postman/               # Postman collection
├── specs/001-strava-insights/    # Spec Kit feature artifacts (spec, plan, tasks, etc.)
├── .specify/                     # Spec Kit framework: constitution, templates, scripts
├── .harness/                     # Node evaluation harness (runner, evaluators, schema, lock)
├── evals/                        # dataset.json and fixtures/
└── evidence/                     # harness results, log, and per-run summaries
```

`.claude/skills/` holds the Spec Kit slash-command definitions (speckit-constitution,
speckit-specify, speckit-clarify, speckit-plan, speckit-tasks, speckit-checklist,
speckit-analyze, speckit-implement, and others). It is git-ignored and is local tooling, not
part of the committed source.

## Spec-driven workflow

The project followed GitHub Spec Kit phases, each producing version-controlled artifacts:

1. **Constitution** — `.specify/memory/constitution.md` (binding principles, e.g. Simplicity;
   Deterministic Core, Non-Deterministic Edge; Data Minimisation; Untrusted Input; Safe File
   Access; Secrets; Evaluation Harness; Change Control).
2. **Specify** — `specs/001-strava-insights/spec.md` (requirements, invariants, scoring).
3. **Clarify** — clarifications recorded in the spec before code changes.
4. **Plan** — `specs/001-strava-insights/plan.md`, with `research.md` and `data-model.md`.
5. **Tasks** — `specs/001-strava-insights/tasks.md`.
6. **Checklists** — `specs/001-strava-insights/checklists/` (`requirements.md`, `spec-rigor.md`).
7. **Analyze / Implement** — harness-first build, then deterministic code, then the model layer.

The API contract is in `specs/001-strava-insights/contracts/api.md`.

## Security and data notes

- **Allow-list sanitisation**: only `id`, `name`, `sport_type`, `distance`, `moving_time`, and
  `start_date` reach the model. Location coordinates and map/polyline data are stripped before
  prompt assembly and never sent to the model, logged, or returned.
- **Secrets**: credentials come only from environment variables. `.env` and secrets are never
  committed (`.gitignore`), and they must not appear in any committed file.
- **Untrusted input**: activity names and the user question are passed to the model inside
  clearly delimited data blocks; the system prompt instructs the model to treat their contents
  as data, not instructions.
- **Sample data**: `data/my-activities.json` is a synthetic sample. Real Strava exports must
  not be committed.
