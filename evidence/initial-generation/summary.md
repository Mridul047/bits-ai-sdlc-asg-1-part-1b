# Initial Generation — Summary

Snapshot of the first end-to-end harness evaluation, taken before any repair.

Files in this folder:
- `eval-results.json` — raw harness output (copy of `evidence/eval-results.json` from this run)
- Harness log: see the single log at `evidence/harness-log.md` (guideline 4.10)
- `summary.md` — this file

## Run details
- Mode: development (1 repetition), 16 cases (TC-001 to TC-016)
- Timestamp: 2026-11-13T06:39:36Z
- Model: `global.anthropic.claude-sonnet-4-5-20250929-v1:0` on AWS Bedrock, region `us-east-1`
- Hashlock: verified clean. Runs excluded: 0 of 16.

## Result: FAIL

| Metric | Value | Requirement |
|---|---|---|
| S (structural) | 1.000 | = 1.0 (met) |
| I (invariant + guardrail) | 0.875 | feeds aggregate |
| Q (semantic) | 0.792 | feeds aggregate |
| Aggregate | 0.867 | >= 0.90 (not met) |
| Guardrail failures | 0 | 0 (met) |

## Failures observed
| Case | Property | What happened |
|---|---|---|
| TC-001 | INV-005 | Insight text contained a date ("January 16, 2026"); the day number could not be traced to a fact. I and Q scored 0. |
| TC-015 | INV-005 | Text contained a date ("2026-01-17") and a derived average ("34 minutes per session"). Guardrails passed. I and Q scored 0. |
| TC-006 | Semantic status | Empty array returned HTTP 422 `INVALID_SOURCE_CONTENT`; spec and dataset expect HTTP 200 `INSUFFICIENT_DATA`. Q scored 0. |
| TC-004 | Semantic (partial) | Focused question returned one insight, so `activity_count` was missing. Q = 0.667. Run still passed. |

## Repairs
None. This snapshot is the code state before repair. Repairs and the final results are documented in `evidence/final-generation/summary.md`.
