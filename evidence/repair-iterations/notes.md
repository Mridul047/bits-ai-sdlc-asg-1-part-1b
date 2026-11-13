# Repair iteration notes

Development-mode (1 repetition) live Bedrock runs performed during the harness-driven repair loop. Each `iteration-N-eval-results.json` in this folder is the raw harness output captured immediately after that iteration's run.

- **Iteration 1** (`iteration-1-eval-results.json`): after the prompt change (forbid dates/derived numbers) and the FileActivitySource empty-array change. Verdict **FAIL**, S=0.938 I=0.938 Q=0.896 Aggregate=0.921. TC-001 and TC-015 INV-005 now pass, but TC-006 regressed to HTTP 500: an empty array reached `FactsCalculator`, whose `max(...).orElseThrow()` threw on the empty stream and mapped to INTERNAL_ERROR (invalid error `code` vs schema → structural fail).
- **Iteration 2** (`iteration-2-eval-results.json`): after guarding `FactsCalculator.compute` to return zeroed facts for an empty list. Verdict **PASS**, S=1.000 I=1.000 Q=0.958 Aggregate=0.983. TC-006 now returns HTTP 200 `INSUFFICIENT_DATA`. Residual Q below 1.0 comes from TC-001 and TC-004 returning focused (subset) answers for focused questions, which is model-wording variance, not a failure.
