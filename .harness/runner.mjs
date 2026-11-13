#!/usr/bin/env node
/**
 * Evaluation harness runner for Strava Activity Insights Generator.
 *
 * Usage:
 *   node runner.mjs --mode development [--port 8080] [--jar path/to/app.jar]
 *   node runner.mjs --mode final [--port 8080] [--jar path/to/app.jar]
 *
 * Steps:
 *   1. Verify hashlock.json — stop if any file hash mismatches
 *   2. Start the Spring Boot jar (pointing source-dir at evals/fixtures/)
 *   3. Run each case in evals/dataset.json (1x development, 3x final)
 *   4. Evaluate each response through structural, invariant, guardrail, semantic layers
 *   5. Compute S, I, Q, Aggregate scores
 *   6. Write evidence/eval-results.json
 *   7. Print verdict: PASS / FAIL / INCONCLUSIVE
 */

import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawn } from 'node:child_process';

import { evaluateStructural } from './evaluators/structural.mjs';
import { evaluateInvariants } from './evaluators/invariant.mjs';
import { evaluateGuardrails } from './evaluators/guardrail.mjs';
import { evaluateSemantic } from './evaluators/semantic.mjs';

const __dirname = dirname(fileURLToPath(import.meta.url));
const ROOT = resolve(__dirname, '..');

// ── CLI args ──────────────────────────────────────────────────────────────────

const args = process.argv.slice(2);
const mode = args.includes('--mode')
  ? args[args.indexOf('--mode') + 1]
  : 'development';
const port = args.includes('--port')
  ? parseInt(args[args.indexOf('--port') + 1], 10)
  : 8080;
const jarPath = args.includes('--jar')
  ? args[args.indexOf('--jar') + 1]
  : resolve(ROOT, 'target/athlenz-strava-agent-0.0.1-SNAPSHOT.jar');

if (!['development', 'final'].includes(mode)) {
  console.error(`Unknown mode "${mode}". Use --mode development|final`);
  process.exit(1);
}

// ── Load config ───────────────────────────────────────────────────────────────

const thresholds = JSON.parse(
  readFileSync(resolve(__dirname, 'thresholds.json'), 'utf8')
);
const repetitions = thresholds.repetitions[mode];
const dataset = JSON.parse(
  readFileSync(resolve(ROOT, 'evals/dataset.json'), 'utf8')
);

// ── Hash lock verification ─────────────────────────────────────────────────────

function sha256File(filePath) {
  const content = readFileSync(filePath);
  return createHash('sha256').update(content).digest('hex');
}

function verifyHashLock() {
  const lockPath = resolve(__dirname, 'hashlock.json');
  if (!existsSync(lockPath)) {
    console.warn('⚠  No hashlock.json found — skipping integrity check.');
    console.warn('   Run generate-hashlock.mjs to create the lock before final evaluation.');
    return;
  }

  const lock = JSON.parse(readFileSync(lockPath, 'utf8'));
  let failed = false;
  for (const [relPath, expectedHash] of Object.entries(lock.files)) {
    const absPath = resolve(ROOT, relPath);
    if (!existsSync(absPath)) {
      console.error(`✗ Hash lock: missing file ${relPath}`);
      failed = true;
      continue;
    }
    const actual = sha256File(absPath);
    if (actual !== expectedHash) {
      console.error(
        `✗ Hash lock mismatch: ${relPath}\n  expected ${expectedHash}\n  actual   ${actual}`
      );
      failed = true;
    }
  }

  if (failed) {
    console.error('\nHash lock verification FAILED. Harness files have been modified.');
    process.exit(2);
  }
  console.log('✓ Hash lock verified');
}

// ── App lifecycle ─────────────────────────────────────────────────────────────

async function startApp() {
  if (!existsSync(jarPath)) {
    console.error(`JAR not found at ${jarPath}. Run: mvn clean package -DskipTests`);
    process.exit(1);
  }

  const env = {
    ...process.env,
    INSIGHTS_SOURCE_DIR: resolve(ROOT, 'evals/fixtures'),
    SERVER_PORT: String(port),
  };

  const required = ['AWS_ACCESS_KEY_ID', 'AWS_SECRET_ACCESS_KEY', 'AWS_REGION', 'BEDROCK_MODEL_ID'];
  for (const key of required) {
    if (!env[key]) {
      console.error(`Missing required env var: ${key}`);
      process.exit(1);
    }
  }

  console.log(`Starting app on port ${port}...`);
  const proc = spawn('java', ['-jar', jarPath, `--server.port=${port}`], {
    env,
    stdio: ['ignore', 'pipe', 'pipe'],
  });

  proc.stdout.on('data', (d) => {
    if (process.env.HARNESS_VERBOSE) process.stdout.write(d);
  });
  proc.stderr.on('data', (d) => {
    if (process.env.HARNESS_VERBOSE) process.stderr.write(d);
  });

  // Wait for health
  await waitForHealth(port, 60_000);
  console.log(`✓ App started on port ${port}`);
  return proc;
}

async function waitForHealth(port, timeoutMs) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    try {
      const res = await fetch(`http://localhost:${port}/actuator/health`);
      if (res.ok) return;
    } catch (_) {
      // not ready yet
    }
    await sleep(1000);
  }
  // Try the main endpoint as fallback
  const start = Date.now();
  while (Date.now() - start < 5000) {
    try {
      await fetch(`http://localhost:${port}/api/insights`, { method: 'POST', body: '{}', headers: { 'Content-Type': 'application/json' } });
      return;
    } catch (_) {}
    await sleep(500);
  }
  throw new Error(`App did not start within ${timeoutMs}ms`);
}

function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

// ── Run cases ─────────────────────────────────────────────────────────────────

async function runCase(testCase, rep) {
  const url = `http://localhost:${port}/api/insights`;
  let httpStatus, body;

  try {
    const res = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(testCase.request),
      signal: AbortSignal.timeout(35_000),
    });
    httpStatus = res.status;
    body = await res.json().catch(() => null);
  } catch (err) {
    return {
      caseId: testCase.id,
      rep,
      excluded: true,
      exclusionReason: `fetch error: ${err.message}`,
      scores: { S: 0, I: 0, Q: 0 },
    };
  }

  // Check excluded codes
  const bodyCode = body?.code;
  if (thresholds.excluded_codes.includes(bodyCode)) {
    return {
      caseId: testCase.id,
      rep,
      httpStatus,
      body,
      excluded: true,
      exclusionReason: `excluded code: ${bodyCode}`,
      scores: { S: 0, I: 0, Q: 0 },
    };
  }

  // Load fixture activities for invariant checks
  let fixtureActivities = [];
  if (testCase.fixture) {
    try {
      fixtureActivities = JSON.parse(
        readFileSync(resolve(ROOT, `evals/fixtures/${testCase.fixture}`), 'utf8')
      );
      if (!Array.isArray(fixtureActivities)) fixtureActivities = [];
    } catch (_) {}
  }

  // Evaluate layers
  const structural = evaluateStructural(httpStatus, body);
  const invariant =
    httpStatus === 200
      ? evaluateInvariants(body, fixtureActivities)
      : { inv002: true, inv003: true, inv004: true, inv005: true, errors: [] };
  const guardrail = evaluateGuardrails(
    testCase.guardrail_tags,
    testCase,
    httpStatus,
    body
  );
  const semantic = evaluateSemantic(
    testCase,
    httpStatus,
    body,
    structural,
    invariant
  );

  const structuralPass = structural.inv001 && structural.inv006;
  const invariantPass =
    invariant.inv002 &&
    invariant.inv003 &&
    invariant.inv004 &&
    invariant.inv005;
  const guardrailPass = Object.entries(guardrail)
    .filter(([k]) => k !== 'errors')
    .every(([, v]) => v === null || v === true);

  return {
    caseId: testCase.id,
    rep,
    httpStatus,
    body,
    excluded: false,
    structural,
    invariant,
    guardrail,
    semantic,
    layerResults: { structuralPass, invariantPass, guardrailPass },
    scores: {
      S: structuralPass ? 1 : 0,
      I: structuralPass && invariantPass && guardrailPass ? 1 : 0,
      Q: semantic.score,
    },
  };
}

// ── Scoring ───────────────────────────────────────────────────────────────────

function computeScores(runResults) {
  const scored = runResults.filter((r) => !r.excluded);
  const excluded = runResults.filter((r) => r.excluded);

  if (scored.length === 0) {
    return { S: 0, I: 0, Q: 0, aggregate: 0, scored: 0, excluded: excluded.length };
  }

  const S = scored.filter((r) => r.scores.S === 1).length / scored.length;
  const I = scored.filter((r) => r.scores.I === 1).length / scored.length;
  const Q = scored.reduce((sum, r) => sum + r.scores.Q, 0) / scored.length;
  const { weights } = thresholds;
  const aggregate = weights.S * S + weights.I * I + weights.Q * Q;

  return { S, I, Q, aggregate, scored: scored.length, excluded: excluded.length };
}

function computeVerdict(scores, runResults) {
  const totalRuns = runResults.length;
  const excludedRatio = scores.excluded / totalRuns;

  if (excludedRatio > thresholds.inconclusive.excluded_run_ratio_max) {
    return 'INCONCLUSIVE';
  }

  const guardrailRuns = runResults.filter(
    (r) => !r.excluded && r.layerResults && !r.layerResults.guardrailPass
  );

  if (
    scores.aggregate >= thresholds.pass.aggregate_min &&
    scores.S >= thresholds.pass.S_min &&
    guardrailRuns.length === 0
  ) {
    return 'PASS';
  }

  return 'FAIL';
}

// ── Main ──────────────────────────────────────────────────────────────────────

async function main() {
  console.log(`\n=== Strava Insights Evaluation Harness ===`);
  console.log(`Mode: ${mode} | Repetitions: ${repetitions} | Cases: ${dataset.length}\n`);

  verifyHashLock();

  const appProcess = await startApp();

  const allRunResults = [];
  const traceabilityMatrix = {};

  try {
    for (const testCase of dataset) {
      for (const reqId of testCase.requirement_ids ?? []) {
        if (!traceabilityMatrix[reqId]) traceabilityMatrix[reqId] = [];
        traceabilityMatrix[reqId].push(testCase.id);
      }

      for (let rep = 1; rep <= repetitions; rep++) {
        process.stdout.write(`  ${testCase.id} (rep ${rep}/${repetitions})... `);
        const runResult = await runCase(testCase, rep);
        allRunResults.push(runResult);

        const icon = runResult.excluded
          ? '⊘'
          : runResult.layerResults?.structuralPass && runResult.layerResults?.invariantPass
          ? '✓'
          : '✗';
        console.log(icon);
      }
    }
  } finally {
    appProcess.kill();
  }

  const scores = computeScores(allRunResults);
  const verdict = computeVerdict(scores, allRunResults);

  // Build failure details
  const failures = allRunResults
    .filter(
      (r) =>
        !r.excluded &&
        (!r.layerResults?.structuralPass || !r.layerResults?.invariantPass || !r.layerResults?.guardrailPass)
    )
    .map((r) => ({
      caseId: r.caseId,
      rep: r.rep,
      httpStatus: r.httpStatus,
      input: dataset.find((c) => c.id === r.caseId)?.request,
      output: r.body,
      structuralErrors: r.structural?.errors ?? [],
      invariantErrors: [...(r.invariant?.errors ?? []), ...(r.guardrail?.errors ?? [])],
    }));

  const evidenceDir = resolve(ROOT, 'evidence');
  const output = {
    verdict,
    mode,
    timestamp: new Date().toISOString(),
    scores: {
      S: +scores.S.toFixed(4),
      I: +scores.I.toFixed(4),
      Q: +scores.Q.toFixed(4),
      aggregate: +scores.aggregate.toFixed(4),
    },
    runs: { total: allRunResults.length, scored: scores.scored, excluded: scores.excluded },
    traceabilityMatrix,
    failures,
    runResults: allRunResults,
  };

  writeFileSync(resolve(evidenceDir, 'eval-results.json'), JSON.stringify(output, null, 2));

  console.log(`\n${'─'.repeat(50)}`);
  console.log(`S=${scores.S.toFixed(3)}  I=${scores.I.toFixed(3)}  Q=${scores.Q.toFixed(3)}  Aggregate=${scores.aggregate.toFixed(3)}`);
  console.log(`Runs: ${scores.scored} scored, ${scores.excluded} excluded of ${allRunResults.length} total`);
  console.log(`\nVerdict: ${verdict}`);
  console.log(`Results written to evidence/eval-results.json`);

  if (verdict === 'FAIL' && failures.length > 0) {
    console.log(`\nFailed runs (${failures.length}):`);
    for (const f of failures) {
      console.log(`  ${f.caseId} rep=${f.rep} HTTP=${f.httpStatus}`);
      for (const e of [...f.structuralErrors, ...f.invariantErrors]) {
        console.log(`    • ${e}`);
      }
    }
  }

  process.exit(verdict === 'PASS' ? 0 : verdict === 'INCONCLUSIVE' ? 2 : 1);
}

main().catch((err) => {
  console.error('Harness error:', err);
  process.exit(1);
});
