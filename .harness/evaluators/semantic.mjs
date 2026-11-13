/**
 * Layer 3 evaluator — semantic / ground-truth comparison.
 * For each expected fact in the test case, checks whether the response
 * correctly reflects it. Score = fraction of expected facts satisfied.
 * Returns 1.0 when no expected facts are defined.
 */

/**
 * @param {object} testCase - from dataset.json, may have expected_facts
 * @param {number} httpStatus
 * @param {object} body - parsed response body
 * @param {{ inv001: boolean, inv006: boolean }} structuralResult
 * @param {{ inv002: boolean, inv003: boolean, inv004: boolean, inv005: boolean }} invariantResult
 * @returns {{ score: number, details: string[] }}
 */
export function evaluateSemantic(
  testCase,
  httpStatus,
  body,
  structuralResult,
  invariantResult
) {
  // A run failing structural or invariant checks scores 0
  const structuralPass = structuralResult.inv001 && structuralResult.inv006;
  const invariantPass =
    invariantResult.inv002 &&
    invariantResult.inv003 &&
    invariantResult.inv004 &&
    invariantResult.inv005;

  if (!structuralPass || !invariantPass) {
    return {
      score: 0,
      details: ['Score 0: structural or invariant check failed'],
    };
  }

  const expectedFacts = testCase.expected_facts;
  if (!expectedFacts || Object.keys(expectedFacts).length === 0) {
    return { score: 1.0, details: ['No expected facts defined — score 1.0'] };
  }

  const details = [];
  let satisfied = 0;
  const total = Object.keys(expectedFacts).length;

  const insights = body?.insights ?? [];

  for (const [factKey, expectedValue] of Object.entries(expectedFacts)) {
    switch (factKey) {
      case 'activity_count': {
        const insight = insights.find((i) => i.metric === 'ACTIVITY_COUNT');
        const got = insight?.value;
        if (got !== undefined && Math.abs(got - expectedValue) <= 0.05) {
          satisfied++;
          details.push(`✓ activity_count: ${got} matches expected ${expectedValue}`);
        } else {
          details.push(`✗ activity_count: got ${got}, expected ${expectedValue}`);
        }
        break;
      }
      case 'total_distance_km': {
        const insight = insights.find((i) => i.metric === 'TOTAL_DISTANCE_KM');
        const got = insight?.value;
        if (got !== undefined && Math.abs(got - expectedValue) <= 0.05) {
          satisfied++;
          details.push(`✓ total_distance_km: ${got}`);
        } else {
          details.push(`✗ total_distance_km: got ${got}, expected ${expectedValue}`);
        }
        break;
      }
      case 'total_moving_time_min': {
        const insight = insights.find((i) => i.metric === 'TOTAL_MOVING_TIME_MIN');
        const got = insight?.value;
        if (got !== undefined && Math.abs(got - expectedValue) <= 0.05) {
          satisfied++;
          details.push(`✓ total_moving_time_min: ${got}`);
        } else {
          details.push(`✗ total_moving_time_min: got ${got}, expected ${expectedValue}`);
        }
        break;
      }
      case 'longest_activity_id': {
        const insight = insights.find(
          (i) => i.metric === 'LONGEST_ACTIVITY_DISTANCE_KM'
        );
        const eids = insight?.evidence_activity_ids ?? [];
        if (eids.includes(expectedValue)) {
          satisfied++;
          details.push(`✓ longest_activity_id: ${expectedValue} cited`);
        } else {
          details.push(
            `✗ longest_activity_id: expected ${expectedValue} in ${JSON.stringify(eids)}`
          );
        }
        break;
      }
      case 'longest_distance_km': {
        const insight = insights.find(
          (i) => i.metric === 'LONGEST_ACTIVITY_DISTANCE_KM'
        );
        const got = insight?.value;
        if (got !== undefined && Math.abs(got - expectedValue) <= 0.05) {
          satisfied++;
          details.push(`✓ longest_distance_km: ${got}`);
        } else {
          details.push(`✗ longest_distance_km: got ${got}, expected ${expectedValue}`);
        }
        break;
      }
      case 'dominant_sport': {
        const insight = insights.find((i) => i.metric === 'DOMINANT_SPORT');
        const got = insight?.value;
        if (got === expectedValue) {
          satisfied++;
          details.push(`✓ dominant_sport: "${got}"`);
        } else {
          details.push(`✗ dominant_sport: got "${got}", expected "${expectedValue}"`);
        }
        break;
      }
      case 'expected_status': {
        const got = body?.status ?? body?.code;
        if (got === expectedValue) {
          satisfied++;
          details.push(`✓ status: ${got}`);
        } else {
          details.push(`✗ status: got "${got}", expected "${expectedValue}"`);
        }
        break;
      }
      default:
        details.push(`? unknown fact key "${factKey}" — skipped`);
        break;
    }
  }

  return {
    score: total > 0 ? satisfied / total : 1.0,
    details,
  };
}
