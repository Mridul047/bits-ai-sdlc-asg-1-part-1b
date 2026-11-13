/**
 * Self-tests for harness evaluators.
 * Run with: node evaluator-tests.mjs
 * Exit 0 = all pass, exit 1 = failures.
 */

import { evaluateStructural } from '../evaluators/structural.mjs';
import { evaluateInvariants, recomputeFacts } from '../evaluators/invariant.mjs';
import { evaluateGuardrails } from '../evaluators/guardrail.mjs';
import { evaluateSemantic } from '../evaluators/semantic.mjs';

let passed = 0;
let failed = 0;

function assert(label, condition, detail = '') {
  if (condition) {
    console.log(`  ✓ ${label}`);
    passed++;
  } else {
    console.error(`  ✗ ${label}${detail ? ': ' + detail : ''}`);
    failed++;
  }
}

// ── Fixtures ──────────────────────────────────────────────────────────────────

const FIVE_ACTIVITIES = [
  { id: 1, name: 'Morning Run', sport_type: 'Run', distance: 5000.0, moving_time: 1800, start_date: '2026-01-15T07:30:00Z' },
  { id: 2, name: 'Long Ride', sport_type: 'Ride', distance: 30000.0, moving_time: 5400, start_date: '2026-01-16T09:00:00Z' },
  { id: 3, name: 'Evening Run', sport_type: 'Run', distance: 8000.0, moving_time: 2700, start_date: '2026-01-17T18:00:00Z' },
  { id: 4, name: 'Quick Run', sport_type: 'Run', distance: 3000.0, moving_time: 900, start_date: '2026-01-18T06:00:00Z' },
  { id: 5, name: 'Weekend Ride', sport_type: 'Ride', distance: 15000.0, moving_time: 3600, start_date: '2026-01-19T10:00:00Z' },
];

const VALID_RESPONSE = {
  status: 'OK',
  summary: 'You have completed 5 activities covering 61.0 km.',
  insights: [
    { metric: 'ACTIVITY_COUNT', text: 'You have 5 activities.', confidence: 'HIGH', evidence_activity_ids: [1, 2, 3, 4, 5], value: 5, unit: 'count' },
    { metric: 'TOTAL_DISTANCE_KM', text: 'Total distance is 61.0 km.', confidence: 'HIGH', evidence_activity_ids: [1, 2, 3, 4, 5], value: 61.0, unit: 'km' },
    { metric: 'TOTAL_MOVING_TIME_MIN', text: 'Total time is 240.0 min.', confidence: 'HIGH', evidence_activity_ids: [1, 2, 3, 4, 5], value: 240.0, unit: 'min' },
    { metric: 'LONGEST_ACTIVITY_DISTANCE_KM', text: 'Your longest activity was 30.0 km.', confidence: 'HIGH', evidence_activity_ids: [2], value: 30.0, unit: 'km' },
    { metric: 'DOMINANT_SPORT', text: 'Your dominant sport is Run.', confidence: 'HIGH', evidence_activity_ids: [1, 3, 4], value: 'Run', unit: 'sport' },
  ],
  metadata: { activities_analysed: 5, source: 'five-activities.json' },
};

const INSUFFICIENT_RESPONSE = {
  status: 'INSUFFICIENT_DATA',
  summary: 'The file contains fewer than 3 activities.',
  insights: [],
  metadata: { activities_analysed: 2, source: 'two-activities.json' },
};

const REFUSED_RESPONSE = {
  status: 'REFUSED',
  summary: 'This question is outside the scope of activity metrics analysis.',
  insights: [],
  metadata: { activities_analysed: 5, source: 'five-activities.json' },
};

const ERROR_RESPONSE = { code: 'INVALID_SOURCE_NAME', message: 'Invalid source name' };

// ── Structural evaluator tests ────────────────────────────────────────────────

console.log('\n--- Structural evaluator ---');

{
  const r = evaluateStructural(200, VALID_RESPONSE);
  assert('valid OK response passes INV-001', r.inv001);
  assert('valid OK response passes INV-006 (5 insights)', r.inv006);
  assert('no errors', r.errors.length === 0, r.errors.join('; '));
}

{
  const r = evaluateStructural(200, INSUFFICIENT_RESPONSE);
  assert('INSUFFICIENT_DATA passes INV-001', r.inv001);
  assert('INSUFFICIENT_DATA passes INV-006 (empty insights)', r.inv006);
}

{
  const r = evaluateStructural(200, REFUSED_RESPONSE);
  assert('REFUSED passes INV-001', r.inv001);
  assert('REFUSED passes INV-006 (empty insights)', r.inv006);
}

{
  const r = evaluateStructural(400, ERROR_RESPONSE);
  assert('400 error response passes INV-001 schema', r.inv001);
}

{
  const badResponse = { status: 'OK', summary: 'x', insights: [], metadata: { activities_analysed: 5, source: 'f.json' } };
  const r = evaluateStructural(200, badResponse);
  assert('OK with 0 insights fails INV-006', !r.inv006);
}

{
  const badResponse = { ...VALID_RESPONSE, status: 'UNKNOWN_STATUS' };
  const r = evaluateStructural(200, badResponse);
  assert('invalid status enum fails INV-001', !r.inv001);
}

// ── Invariant evaluator tests ─────────────────────────────────────────────────

console.log('\n--- Invariant evaluator ---');

{
  const r = evaluateInvariants(VALID_RESPONSE, FIVE_ACTIVITIES);
  assert('correct response passes INV-002', r.inv002);
  assert('correct response passes INV-003', r.inv003);
  assert('correct response passes INV-004', r.inv004);
  assert('correct response passes INV-005', r.inv005);
  assert('no errors', r.errors.length === 0, r.errors.join('; '));
}

{
  const wrongValue = JSON.parse(JSON.stringify(VALID_RESPONSE));
  wrongValue.insights[1].value = 99.0; // wrong total distance
  const r = evaluateInvariants(wrongValue, FIVE_ACTIVITIES);
  assert('wrong TOTAL_DISTANCE_KM value fails INV-003', !r.inv003, r.errors.join('; '));
}

{
  const wrongId = JSON.parse(JSON.stringify(VALID_RESPONSE));
  wrongId.insights[3].evidence_activity_ids = [999]; // non-existent ID
  const r = evaluateInvariants(wrongId, FIVE_ACTIVITIES);
  assert('non-existent evidence ID fails INV-002', !r.inv002, r.errors.join('; '));
}

{
  const wrongLongest = JSON.parse(JSON.stringify(VALID_RESPONSE));
  wrongLongest.insights[3].evidence_activity_ids = [1]; // wrong ID for longest
  const r = evaluateInvariants(wrongLongest, FIVE_ACTIVITIES);
  assert('LONGEST not citing max-distance activity fails INV-004', !r.inv004, r.errors.join('; '));
}

{
  const facts = recomputeFacts(FIVE_ACTIVITIES);
  assert('recomputeFacts: activityCount=5', facts.activityCount === 5);
  assert('recomputeFacts: totalDistanceKm=61.0', facts.totalDistanceKm === 61.0);
  assert('recomputeFacts: totalMovingTimeMin=240.0', facts.totalMovingTimeMin === 240.0);
  assert('recomputeFacts: longestId=2', facts.longestId === 2);
  assert('recomputeFacts: dominantSport=Run', facts.dominantSport === 'Run');
}

// ── Guardrail evaluator tests ─────────────────────────────────────────────────

console.log('\n--- Guardrail evaluator ---');

{
  const r = evaluateGuardrails(['GR-002'], {}, 200, REFUSED_RESPONSE);
  assert('medical question REFUSED passes GR-002', r.gr002 === true);
}

{
  const r = evaluateGuardrails(['GR-002'], {}, 200, VALID_RESPONSE);
  assert('medical question OK fails GR-002', r.gr002 === false, r.errors.join('; '));
}

{
  const r = evaluateGuardrails(['GR-003'], {}, 200, REFUSED_RESPONSE);
  assert('other-athletes question REFUSED passes GR-003', r.gr003 === true);
}

{
  const r = evaluateGuardrails(['GR-005'], {}, 400, ERROR_RESPONSE);
  assert('path traversal 400 INVALID_SOURCE_NAME passes GR-005', r.gr005 === true);
}

{
  const r = evaluateGuardrails(['GR-005'], {}, 200, VALID_RESPONSE);
  assert('no INVALID_SOURCE_NAME fails GR-005', r.gr005 === false);
}

{
  const bodyWithSecret = { ...VALID_RESPONSE, summary: 'AWS_ACCESS_KEY_ID is AKIAIOSFODNN7EXAMPLE' };
  const r = evaluateGuardrails([], {}, 200, bodyWithSecret);
  assert('response with AWS key fails GR-004', r.gr004 === false, r.errors.join('; '));
}

{
  const r = evaluateGuardrails(['GR-001'], {}, 200, VALID_RESPONSE);
  assert('response without injection echo passes GR-001', r.gr001 === true);
}

// ── Semantic evaluator tests ──────────────────────────────────────────────────

console.log('\n--- Semantic evaluator ---');

const structuralPass = { inv001: true, inv006: true };
const invariantPass = { inv002: true, inv003: true, inv004: true, inv005: true };
const structuralFail = { inv001: false, inv006: true };

{
  const tc = { expected_facts: { total_distance_km: 61.0, dominant_sport: 'Run' } };
  const r = evaluateSemantic(tc, 200, VALID_RESPONSE, structuralPass, invariantPass);
  assert('correct facts score 1.0', r.score === 1.0, `score=${r.score}`);
}

{
  const tc = { expected_facts: { total_distance_km: 999.0 } }; // wrong expected
  const r = evaluateSemantic(tc, 200, VALID_RESPONSE, structuralPass, invariantPass);
  assert('wrong fact score < 1.0', r.score < 1.0, `score=${r.score}`);
}

{
  const tc = { expected_facts: { total_distance_km: 61.0 } };
  const r = evaluateSemantic(tc, 200, VALID_RESPONSE, structuralFail, invariantPass);
  assert('structural failure scores 0', r.score === 0, `score=${r.score}`);
}

{
  const tc = {}; // no expected_facts
  const r = evaluateSemantic(tc, 200, VALID_RESPONSE, structuralPass, invariantPass);
  assert('no expected facts scores 1.0', r.score === 1.0, `score=${r.score}`);
}

// ── Summary ───────────────────────────────────────────────────────────────────

console.log(`\n${'─'.repeat(40)}`);
console.log(`Self-tests: ${passed} passed, ${failed} failed`);

if (failed > 0) {
  console.error('\nSelf-test failures detected. Fix evaluators before running harness.');
  process.exit(1);
} else {
  console.log('All self-tests passed.');
  process.exit(0);
}
