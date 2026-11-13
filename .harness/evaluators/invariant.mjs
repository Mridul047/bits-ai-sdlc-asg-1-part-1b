/**
 * Layer 2 evaluator — invariant checks.
 * Independently recomputes facts from fixture data and verifies:
 *   INV-002: all cited IDs exist in input
 *   INV-003: each cited value matches recomputed fact within 0.05 tolerance
 *   INV-004: LONGEST cites max-distance activity; DOMINANT names top sport
 *   INV-005: numbers in insight text are consistent with recomputed facts
 */

/**
 * Recompute facts from raw fixture activities (before allow-list stripping).
 * Mirrors FactsCalculator logic exactly.
 * @param {object[]} activities
 */
export function recomputeFacts(activities) {
  const count = activities.length;
  const totalDistanceKm =
    Math.round((activities.reduce((s, a) => s + a.distance, 0) / 1000) * 10) / 10;
  const totalMovingTimeMin =
    Math.round((activities.reduce((s, a) => s + a.moving_time, 0) / 60) * 10) / 10;

  let longest = activities[0];
  for (const a of activities) {
    if (a.distance > longest.distance) longest = a;
  }
  const longestDistanceKm = Math.round((longest.distance / 1000) * 10) / 10;
  const longestId = longest.id;

  const sportCounts = {};
  for (const a of activities) {
    sportCounts[a.sport_type] = (sportCounts[a.sport_type] ?? 0) + 1;
  }
  const dominantSport = Object.entries(sportCounts).sort(
    (a, b) => b[1] - a[1]
  )[0][0];

  return {
    activityCount: count,
    totalDistanceKm,
    totalMovingTimeMin,
    longestDistanceKm,
    longestId,
    dominantSport,
    allIds: new Set(activities.map((a) => a.id)),
    sportCounts,
  };
}

const METRIC_FACTS = {
  ACTIVITY_COUNT: (facts) => facts.activityCount,
  TOTAL_DISTANCE_KM: (facts) => facts.totalDistanceKm,
  TOTAL_MOVING_TIME_MIN: (facts) => facts.totalMovingTimeMin,
  LONGEST_ACTIVITY_DISTANCE_KM: (facts) => facts.longestDistanceKm,
  DOMINANT_SPORT: (facts) => facts.dominantSport,
};

/**
 * @param {object} body - parsed HTTP 200 response
 * @param {object[]} fixtureActivities - raw activities from fixture file
 * @returns {{ inv002: boolean, inv003: boolean, inv004: boolean, inv005: boolean, errors: string[] }}
 */
export function evaluateInvariants(body, fixtureActivities) {
  const result = {
    inv002: true,
    inv003: true,
    inv004: true,
    inv005: true,
    errors: [],
  };

  if (body?.status !== 'OK' || !Array.isArray(body?.insights)) {
    // Invariants INV-002 to INV-005 only apply to OK responses with insights
    return result;
  }

  const facts = recomputeFacts(fixtureActivities);

  for (const insight of body.insights) {
    const { metric, value, evidence_activity_ids: eids, text } = insight;

    // INV-002: all cited IDs exist
    for (const id of eids ?? []) {
      if (!facts.allIds.has(id)) {
        result.inv002 = false;
        result.errors.push(`INV-002: insight ${metric} cites id=${id} not in input`);
      }
    }

    // INV-003: cited value matches recomputed
    const expectedFn = METRIC_FACTS[metric];
    if (expectedFn) {
      const expected = expectedFn(facts);
      if (typeof expected === 'number' && typeof value === 'number') {
        if (Math.abs(value - expected) > 0.05) {
          result.inv003 = false;
          result.errors.push(
            `INV-003: ${metric} value=${value} expected=${expected} (diff=${Math.abs(value - expected).toFixed(3)})`
          );
        }
      } else if (typeof expected === 'string') {
        // DOMINANT_SPORT: exact match
        // Allow ties: we can't strictly enforce one value, but check it's a known sport
        const knownSports = new Set(Object.keys(facts.sportCounts));
        if (!knownSports.has(value)) {
          result.inv003 = false;
          result.errors.push(
            `INV-003: DOMINANT_SPORT value="${value}" not a sport in input (known: ${[...knownSports].join(', ')})`
          );
        }
      }
    }

    // INV-004: metric-specific citation rules
    if (metric === 'LONGEST_ACTIVITY_DISTANCE_KM') {
      if (!(eids ?? []).includes(facts.longestId)) {
        result.inv004 = false;
        result.errors.push(
          `INV-004: LONGEST insight eids=${JSON.stringify(eids)} must include longestId=${facts.longestId}`
        );
      }
    }
    if (metric === 'DOMINANT_SPORT') {
      const topCount = facts.sportCounts[value];
      if (topCount === undefined) {
        result.inv004 = false;
        result.errors.push(
          `INV-004: DOMINANT_SPORT "${value}" is not a sport in input`
        );
      } else {
        const maxCount = Math.max(...Object.values(facts.sportCounts));
        // Accept tie: sport cited must be tied for first
        if (topCount < maxCount) {
          result.inv004 = false;
          result.errors.push(
            `INV-004: DOMINANT_SPORT "${value}" count=${topCount} but max is ${maxCount}`
          );
        }
      }
    }

    // INV-005: numbers in text must be consistent with facts
    const numbersInText = [...(text ?? '').matchAll(/\b(\d+(?:\.\d+)?)\b/g)].map(
      (m) => parseFloat(m[1])
    );
    const factsValues = [
      facts.activityCount,
      facts.totalDistanceKm,
      facts.totalMovingTimeMin,
      facts.longestDistanceKm,
    ];
    for (const n of numbersInText) {
      // A number is consistent if it matches a known fact within 0.05 or is a year/ID/count
      const matchesFact = factsValues.some((f) => Math.abs(n - f) <= 0.05);
      const isYear = n >= 2020 && n <= 2030;
      const isId = facts.allIds.has(n);
      if (!matchesFact && !isYear && !isId) {
        result.inv005 = false;
        result.errors.push(
          `INV-005: ${metric} text contains number ${n} not consistent with recomputed facts`
        );
      }
    }
  }

  return result;
}
