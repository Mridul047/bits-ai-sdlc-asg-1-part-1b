# Specification Rigor Checklist: Strava Activity Insights Generator

**Purpose**: Validate requirements quality for the output contract,
invariants, guardrails, error codes, and evaluation scoring — flagging
anything vague, untestable, or missing.

**Created**: 2026-11-11

**Feature**: [spec.md](../spec.md)

**Review Ownership**: This checklist is a reviewer-owned
requirements-quality review artifact. Mark an item `[x]` only when the
reviewer determines the requirements-quality criterion is satisfied.

**Marker Semantics**: `[x]` means the criterion has been reviewed and
satisfied for requirements quality. It does not mean implementation
work is complete.

## Output Contract Completeness & Precision

- [ ] CHK001 - Is the JSON type of `value` for each metric unambiguously specified? The contract says "number or string" but does not state whether ACTIVITY_COUNT value is an integer (5) or a decimal (5.0); the "rounded to one decimal place" rule applies to distances and times but its applicability to counts is unclear. [Clarity, Spec §Output Contract]

- [ ] CHK002 - Is "characters" in the summary (1-400) and text (1-300) length bounds defined — Unicode scalar values, UTF-16 code units, or grapheme clusters? Different interpretations produce different limits for multi-byte text. [Clarity, Spec §Output Contract]

- [ ] CHK003 - Does the output contract's "1-5 items when OK" constraint reflect the clarified FR-015 rule that a general overview MUST produce exactly 5 insights? INV-006 repeats the 1-5 bound without distinguishing the general-overview path, so a harness checking only INV-006 would accept 3 insights for a no-question request. [Consistency, Spec §Output Contract / FR-015 / INV-006]

- [ ] CHK004 - Is the JSON serialization of `metadata` specified as a nested object (`"metadata": {"activities_analysed": 5, "source": "f.json"}`) or as flat fields? The table uses dot notation (`metadata.activities_analysed`) which is ambiguous. [Clarity, Spec §Output Contract]

- [ ] CHK005 - Are requirements defined for the ordering of insights in the array, or is the absence of an ordering requirement explicitly stated? The harness needs to know whether to compare order-sensitively. [Completeness, Gap]

- [ ] CHK006 - Is the JSON representation of `evidence_activity_ids` specified as a JSON array of JSON numbers (not strings)? The contract says "array of ints" but JSON has only "number." [Clarity, Spec §Output Contract]

- [ ] CHK007 - Is the rounding rule for ACTIVITY_COUNT value documented? FR-023 says "distances in kilometres and times in minutes, rounded to one decimal place" — this covers TOTAL_DISTANCE_KM, TOTAL_MOVING_TIME_MIN, and LONGEST_ACTIVITY_DISTANCE_KM but not ACTIVITY_COUNT (which is a whole number) or DOMINANT_SPORT (which is a string). [Completeness, Spec §FR-023]

## Invariant Testability & Precision

- [ ] CHK008 - Does INV-003 ("every cited value equals the independently recomputed value within one decimal place of rounding") specify how the check applies to DOMINANT_SPORT, whose value is a string? String equality has no rounding tolerance. Is the invariant intended to apply only to numeric metrics? [Clarity, Spec §INV-003]

- [ ] CHK009 - Does INV-004 specify the expected behaviour when two or more activities are tied for greatest distance? The spec addresses ties for DOMINANT_SPORT (A-005: either sport acceptable) but not for LONGEST_ACTIVITY_DISTANCE_KM. [Gap, Spec §INV-004]

- [ ] CHK010 - Is INV-005 ("every number appearing in an insight's text") scoped precisely enough for machine checking? Are there criteria for which numbers in the text to extract and compare? A text like "across 5 different weeks" contains a number unrelated to the five metrics. Is the extraction method or scope defined? [Clarity, Spec §INV-005]

- [ ] CHK011 - Does INV-005 define the tolerance for "consistent with the recomputed facts"? Is it the same one-decimal-place rounding tolerance as INV-003, or exact match, or something else? [Clarity, Spec §INV-005]

- [ ] CHK012 - Does INV-001 ("response matches the output contract exactly") specify which response schema applies when the HTTP status is non-200? Error responses use a different contract (code + message) than success responses (status, summary, insights, metadata). Is it stated that INV-001 applies only to HTTP 200 responses? [Clarity, Spec §INV-001]

- [ ] CHK013 - Are the specific JSON field names constituting "location data" in INV-007 enumerated? The spec says "start/end coordinates, map polylines" but does not list the exact field keys (e.g., `start_latlng`, `end_latlng`, `map`, `summary_polyline`). The unit test needs concrete field names to assert against. [Completeness, Spec §INV-007]

- [ ] CHK014 - Does INV-006 state how it applies when the status is INSUFFICIENT_DATA or REFUSED? The invariant says "has 1-5 items when status is OK" and "empty when status is not OK," but the INSUFFICIENT_DATA and REFUSED acceptance scenarios already assert insights is empty. Is this redundancy intentional for independent checking, or is there a risk of divergence? [Consistency, Spec §INV-006]

## Guardrail Testability & Observable Criteria

- [ ] CHK015 - Does GR-001 define specific observable test criteria for "instructions … are never followed"? The harness needs concrete injection payloads and expected non-occurrence patterns. Is a catalogue of injection payloads part of the evaluation dataset specification, or is this left to the harness builder? [Completeness, Spec §GR-001]

- [ ] CHK016 - Does GR-002 define how the harness detects medical advice in model-generated free text? Keyword matching, regex, or semantic analysis? Without a detection method, the guardrail is aspirational rather than machine-checkable. [Measurability, Spec §GR-002]

- [ ] CHK017 - Is GR-002 testable by checking only the `status` field (REFUSED), or must the harness also scan `summary` and insight `text` for medical content on OK responses? The spec says "no medical advice is ever given in any response" which includes OK responses. [Clarity, Spec §GR-002]

- [ ] CHK018 - Does GR-003 define the observable check for "about other athletes" in harness terms? If the expected outcome is `status: REFUSED`, the check is straightforward. But if the question passes refusal detection and status is OK, is the harness expected to scan text for references to other athletes? [Measurability, Spec §GR-003]

- [ ] CHK019 - Does GR-004 specify how to test that "prompt text and internal configuration are never revealed"? The API key has a known value to test against, but prompt text and configuration are unbounded. Is a representative sample sufficient, or must the check be exhaustive? [Measurability, Spec §GR-004]

- [ ] CHK020 - Are GR-001 through GR-004 classified as observable-in-response guardrails or not? The evaluation layers say Layer 2 checks "GR-001 through GR-005 that are observable in output." The qualifier "that are observable" implies some may not be, but the spec does not explicitly classify which guardrails are observable and which are not. [Clarity, Spec §Evaluation Layers]

## Error Code Completeness & Distinction

- [ ] CHK021 - Is the error response for a malformed JSON request body (not the source file — the POST body itself is not valid JSON) specified? None of the seven error codes cover this case. The framework may return its own 400, but is this acceptable or must it conform to the code/message contract? [Gap, Spec §Error Handling]

- [ ] CHK022 - Is it specified what error code applies when the `source` field is present but null, or when the request body is empty? INVALID_SOURCE_NAME covers "empty" source names but not absent/null fields. [Edge Case, Spec §FR-024]

- [ ] CHK023 - Is the ambiguity of MODEL_ERROR and MODEL_OUTPUT_INVALID sharing HTTP 502 acknowledged, and is the machine-readable `code` field stated as the authoritative discriminator for harness scoring? The scoring formula excludes MODEL_ERROR runs but counts MODEL_OUTPUT_INVALID as structural failures — distinguishing them solely by HTTP status is impossible. [Clarity, Spec §Error Handling / Scoring]

- [ ] CHK024 - Is there a specified error code or behaviour for when the model response is truncated due to a max-tokens limit? Is this MODEL_OUTPUT_INVALID (failed validation) or MODEL_ERROR (provider-side truncation)? [Gap, Spec §Error Handling]

- [ ] CHK025 - Is the error response body schema formally specified with the same precision as the success response? FR-031 says "code and message fields" but does not specify types, bounds, or whether additional fields are allowed. [Completeness, Spec §FR-031]

- [ ] CHK026 - Is the validation order between source-name and question validation defined? If both source and question are invalid, which error code takes precedence? [Gap, Spec §Error Handling]

## Evaluation Scoring Precision & Edge Cases

- [ ] CHK027 - Is "scored runs" defined precisely in the scoring formula? The spec says MODEL_ERROR and MODEL_TIMEOUT runs are excluded. Does the harness identify excluded runs by HTTP status code, by the `code` field in the response body, or by exception type? Since MODEL_ERROR and MODEL_OUTPUT_INVALID both return HTTP 502, the identification method matters. [Clarity, Spec §Scoring Formula]

- [ ] CHK028 - Is the denominator for S, I, and Q defined? If there are 48 total runs and 6 are excluded, is S computed as passing-structural / 42? This is implied by "share of scored runs" but not stated explicitly. [Clarity, Spec §Scoring Formula]

- [ ] CHK029 - Is the computation of Q scoped to scored runs only or all runs? The spec says "mean semantic score where a run failing any structural or invariant check scores 0" but does not say whether excluded runs contribute a 0 or are absent from the mean. [Clarity, Spec §Scoring Formula]

- [ ] CHK030 - Is the 20% INCONCLUSIVE threshold computed against total runs or total cases? If there are 16 cases each run 3 times (48 runs) and 10 runs are excluded (20.8%), is this INCONCLUSIVE? Or if 4 out of 16 cases have at least one excluded run (25% of cases), is that the trigger? [Clarity, Spec §Threshold]

- [ ] CHK031 - Is the PASS threshold stated as a conjunction (all three conditions must hold simultaneously)? The spec lists three bullet points but does not use an explicit "AND" or "all of the following." Could a reader interpret them as disjunctive? [Clarity, Spec §Threshold]

- [ ] CHK032 - Does the scoring formula address evaluation cases whose expected outcome is a non-200 error (e.g., a case designed to trigger INVALID_SOURCE_NAME 400)? INV-001 checks the output contract schema, but error responses use a different schema. Are error-targeted cases excluded from the structural layer check, or do they use a separate error-response schema? [Gap, Spec §Evaluation Layers]

- [ ] CHK033 - Does "every guardrail-tagged run passing" in the threshold apply per-run or per-case? If a guardrail case passes on 2 of 3 repetitions but fails once, does it fail the threshold? The repetitions section says "guardrails MUST hold in every repetition" which implies per-run, but the threshold bullet does not cross-reference this. [Consistency, Spec §Threshold / Repetitions]

- [ ] CHK034 - Is MODEL_OUTPUT_INVALID's treatment as a "structural failure" fully specified? The response body for this error is `{"code": "MODEL_OUTPUT_INVALID", "message": "..."}` (error schema), not the output contract. Does "structural failure" mean the run is scored and automatically fails Layer 1, or that the error response itself is structurally validated? [Clarity, Spec §Threshold]

- [ ] CHK035 - Are the three PASS conditions (Aggregate >= 0.90, S = 100%, every guardrail-tagged run passes) logically consistent? If S = 100% is required independently, the 0.2*S term in the aggregate is always 0.2 and never the binding constraint. Is the S threshold redundant with its presence in the formula, and is this intentional? [Consistency, Spec §Threshold / Scoring Formula]

## Notes

- Mark items `[x]` only after review confirms the requirement-quality
  criterion is satisfied.
- Leave items unchecked when they still require clarification,
  correction, or reviewer evaluation.
- `/speckit-implement` reads checklist checkbox state as a gate and
  must not modify markers.
- `checklists/requirements.md` has a separate built-in lifecycle
  maintained by `/speckit-specify` and `/speckit-clarify`.
- Items are numbered sequentially (CHK001-CHK035) for easy reference.
- Each item references the spec section it examines; `[Gap]` marks
  where requirements are absent rather than unclear.
