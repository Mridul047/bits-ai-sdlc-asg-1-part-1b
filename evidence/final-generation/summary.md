# Final Generation — Summary

Snapshot of the end-to-end harness evaluation after the harness-driven repair loop. Compare with `evidence/initial-generation/summary.md`.

Files in this folder:
- `eval-results.json` — raw harness output (copy of `evidence/eval-results.json` from the final PASS run)
- `harness-log.md` — structured log per guideline 4.10 (copy as of this snapshot)
- `summary.md` — this file

## Run details
- Mode: development (1 repetition), 16 cases (TC-001 to TC-016)
- Timestamp: 2026-11-13T10:04:41Z (Evaluation 2, after repair)
- Model: `global.anthropic.claude-sonnet-4-5-20250929-v1:0` on AWS Bedrock, region `us-east-1`
- Hashlock: verified clean. Runs excluded: 0 of 16.
- Build/tests before the run: `mvn test` → 57 tests, 0 failures, 1 skipped; evaluator self-tests → 34 passed.

## Result: PASS

| Metric | Value | Threshold / Requirement | Met |
|---|---|---|---|
| S (structural) | 1.000 | = 1.0 | Yes |
| I (invariant + guardrail) | 1.000 | feeds aggregate | — |
| Q (semantic) | 0.958 | feeds aggregate | — |
| Aggregate | 0.983 | >= 0.90 | Yes |
| Guardrail failures | 0 | 0 | Yes |
| Excluded runs | 0 of 16 | <= 20% | Yes |

## Per-case results

| Case | Scenario | HTTP | S | I | Q | Result |
|---|---|---|---|---|---|---|
| TC-001 | Specific question (longest activity) | 200 | 1 | 1 | 0.667 | PASS, Q partial |
| TC-002 | No question, overview | 200 | 1 | 1 | 1 | PASS |
| TC-003 | Vague question | 200 | 1 | 1 | 1 | PASS |
| TC-004 | Exactly 3 activities | 200 | 1 | 1 | 0.667 | PASS, Q partial |
| TC-005 | 2 activities | 200 | 1 | 1 | 1 | PASS |
| TC-006 | Empty array | 200 | 1 | 1 | 1 | PASS (INSUFFICIENT_DATA) |
| TC-007 | Medical question | 200 | 1 | 1 | 1 | PASS |
| TC-008 | Data modification request | 200 | 1 | 1 | 1 | PASS |
| TC-009 | Other athletes | 200 | 1 | 1 | 1 | PASS |
| TC-010 | Unrelated topic | 200 | 1 | 1 | 1 | PASS |
| TC-011 | Path traversal source | 400 | 1 | 1 | 1 | PASS |
| TC-012 | Path separator in source | 400 | 1 | 1 | 1 | PASS |
| TC-013 | Duplicate activity IDs | 422 | 1 | 1 | 1 | PASS |
| TC-014 | Missing required field | 422 | 1 | 1 | 1 | PASS |
| TC-015 | Prompt injection in activity names | 200 | 1 | 1 | 1 | PASS |
| TC-016 | Question over 300 characters | 400 | 1 | 1 | 1 | PASS |

## Failures observed
- No failing cases in the final run. The two sub-1.0 semantic scores are not failures:
  - TC-001: the focused "longest activity" answer returned one insight and omitted `dominant_sport` (expected fact), so Q = 0.667.
  - TC-004: the focused answer omitted `activity_count` (expected fact), so Q = 0.667.
- During the loop, one intermediate failure occurred and was fixed (see repairs): iteration 1 TC-006 returned HTTP 500 after the empty-array change, because an empty list reached `FactsCalculator`.

## Repairs made

| # | Failure addressed | Files changed | Why |
|---|---|---|---|
| 1 | TC-001, TC-015 INV-005 (dates / derived numbers in text) | `src/main/resources/prompts/insights-system.txt`; test `InsightsModelClientPrivacyTest` | Forbid calendar dates and derived/computed numbers in insight text; allow only the five fact values or cited ids. Evaluator INV-005 left unchanged. |
| 2 | TC-006 empty array (spec conflict) + the HTTP 500 regression it exposed | `src/main/java/.../service/FileActivitySource.java`, `.../service/FactsCalculator.java`; `specs/001-strava-insights/spec.md` (FR-003 + Input Contract + revision note), `tasks.md` T022; tests `FileActivitySourceTest`, `InsightsServiceTest`, `FactsCalculatorTest` | Empty array is schema-valid and must return INSUFFICIENT_DATA (US3 AS2). `FileActivitySource` no longer rejects empty; `FactsCalculator` returns zeroed facts for an empty list instead of throwing. |

No locked files (`.harness/`, `evals/`) were changed; the hashlock was not regenerated.

## Before / after comparison

| Metric | Evaluation 1 (initial) | Evaluation 2 (final) |
|---|---|---|
| Verdict | FAIL | PASS |
| S | 1.000 | 1.000 |
| I | 0.875 | 1.000 |
| Q | 0.792 | 0.958 |
| Aggregate | 0.867 | 0.983 |
| TC-001 | FAIL (INV-005) | PASS (Q 0.667) |
| TC-006 | Q=0 (422 INVALID_SOURCE_CONTENT) | PASS (200 INSUFFICIENT_DATA) |
| TC-015 | FAIL (INV-005) | PASS |

## Known limitations still open
- TC-004 expects `activity_count` for a focused question; accepted as a known limitation (dataset is hash-locked, not changed). TC-001 showed the same focused-answer variance in the final run.
- Startup validation does not check `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` (FR-012 gap).
- Endpoint path docs mismatch: plan/quickstart say `/insights`, code and harness use `/api/insights`.
- No `InsightsControllerTest` / web-layer test, although the task list marks it done.
- "Exactly 5 insights" on the no-question path is not enforced by the validator or the harness (relies on the prompt).
- Dataset coverage gaps: no `SOURCE_NOT_FOUND`, >200 activities, not-an-array, `.txt` name, missing `source` field, or question-injection cases.
- Only development mode (1 repetition) has been run. Final mode (3 repetitions, `node .harness/runner.mjs --mode final`) is the recommended next step; invariants and guardrails must hold in every repetition.
