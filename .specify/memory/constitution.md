<!--
## Sync Impact Report

- **Version change**: (none) → 1.0.0
- **Bump rationale**: MAJOR — initial adoption of project constitution
- **Added sections**:
  - Core Principles: I through XIII (13 principles)
  - Technology Stack Requirements
  - Development Workflow
  - Governance
- **Removed sections**: (none — first version)
- **Modified principles**: (none — first version)
- **Deferred TODOs**: none
-->

# Strava Insights Generator Constitution

## Core Principles

### I. Simplicity

The application exposes one endpoint, uses no database, no UI, no
streaming, no RAG, no conversation memory, and no model-driven tool
calling. Implementation MUST be limited to what the specification
requires. No speculative features, no infrastructure beyond the
minimum.

### II. Deterministic Core, Non-Deterministic Edge

Input loading, validation, sanitisation, statistics computation, and the
INSUFFICIENT_DATA decision MUST be implemented as plain deterministic
code with no model involvement. The model is used only to produce
interpretive insights from pre-computed facts. Model output is never
trusted: it MUST be parsed and validated before it reaches the caller.

### III. Data Minimisation

Location fields (start/end coordinates, map polyline data) and every
field outside the specified allow-list MUST NOT be sent to the model,
logged, or returned in any response. Stripping MUST occur before prompt
assembly.

### IV. Untrusted Input

Activity names and the user question are untrusted data. They MUST be
passed to the model as clearly delimited data sections. Instructions
embedded inside untrusted data MUST NOT be followed by the model prompt
design or by application logic.

### V. Safe File Access

The service reads only a bare file name (never a path) resolved against
one configured base directory. Absolute paths, parent-directory
sequences (`..`), and subfolders MUST be rejected before any I/O
occurs.

### VI. Secrets

The model API key MUST come exclusively from an environment variable. It
MUST NOT be hard-coded, logged, returned in any response, or present in
any committed file. Real Strava exports MUST NOT be committed; only
synthetic or sanitised fixtures are committed, and the live data folder
MUST be git-ignored.

### VII. Failure Separation

Model or service failures (timeouts, rate limits, provider errors) MUST
be reported with distinct error codes and MUST NOT be treated as
output-quality failures. Invalid model output that survives one retry
MUST be classified as an application failure with its own distinct error
code.

### VIII. Reproducibility

Model name, temperature, and timeouts MUST be externalisable
configuration. Prompts MUST be version-controlled files in a `prompts/`
directory. A low temperature MUST be used to reduce output variance.

### IX. Tests

Unit tests, where used, MUST mock the model and MUST NOT call a real
model endpoint. No secrets or real activity data MUST appear in any
committed file.

### X. Evaluation Harness

One runner in `.harness/` evaluates the running service against
`evals/dataset.json` in three layers: structural, invariant/property,
and semantic/quality. It computes the score defined in the specification
and returns PASS, FAIL, or INCONCLUSIVE. The dataset, fixtures, schema,
evaluators, thresholds, and runner MUST be produced in a harness-building
step before any application code exists, using only the constitution,
specification, clarifications, and plan. They are reviewed and approved
by the human and locked by hash. Implementation code MUST NOT create or
modify harness artifacts.

### XI. Agent Restrictions

The coding agent MUST NOT weaken acceptance criteria, remove or edit
evaluation cases or expected values, lower thresholds, disable or loosen
guardrails, skip harness stages, edit recorded results, or modify any
locked harness file to obtain a pass. The application MUST NOT
special-case evaluation inputs: no hard-coded responses keyed to dataset
case IDs, fixture names, question text, or canary strings. If the agent
believes the specification is wrong, it MUST stop and say so.

### XII. Traceability

Every requirement ID in the specification MUST be covered by at least
one unit test or evaluation case that names that requirement ID
explicitly.

### XIII. Change Control

Any ambiguity or requirement change MUST be recorded in the Spec Kit
artifacts before code is changed. The evaluation threshold, once
declared, MUST be changed only through a recorded specification
revision.

## Technology Stack Requirements

- **Language**: Java 21
- **Framework**: Spring Boot 4.x with Spring AI 2.x
- **Build tool**: Maven
- **API style**: current Spring Boot 4 and Spring AI 2 starters and
  APIs only; Spring Boot 3-era class names and Jackson 2
  (`com.fasterxml.jackson`) packages MUST NOT be used
- All principles in this constitution are binding on every later
  specification, planning, implementation, and evaluation step

## Development Workflow

1. **Harness-first**: the evaluation harness (dataset, fixtures, schema,
   evaluators, thresholds, runner) is built, reviewed, and hash-locked
   before any application code is written.
2. **Spec-driven**: code changes follow from specification changes, never
   the reverse. Ambiguities are resolved in Spec Kit artifacts first.
3. **Deterministic before non-deterministic**: all deterministic logic
   (validation, statistics, sanitisation) is implemented and tested
   before the model-calling layer is wired in.
4. **Model isolation**: unit tests never call a real model. The model
   boundary is behind a mockable interface.
5. **Evaluation gating**: the harness score MUST meet the declared
   threshold before a change is considered complete.

## Governance

This constitution supersedes all other development practices for the
Strava Insights Generator project. It is the authoritative source of
binding constraints.

- **Amendment procedure**: any change to this constitution MUST be
  proposed, reviewed, and recorded with a version increment before
  taking effect. Changes to evaluation thresholds require a recorded
  specification revision.
- **Versioning policy**: this document follows semantic versioning.
  MAJOR for principle removals or incompatible redefinitions; MINOR
  for new principles or material expansions; PATCH for clarifications
  and wording fixes.
- **Compliance review**: every specification, plan, implementation
  change, and evaluation run MUST be verifiable against these
  principles. The coding agent MUST halt and report if it detects a
  conflict between the specification and this constitution.

**Version**: 1.0.0 | **Ratified**: 2026-11-10 | **Last Amended**: 2026-11-10
