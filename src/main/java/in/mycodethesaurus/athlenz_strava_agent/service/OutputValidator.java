package in.mycodethesaurus.athlenz_strava_agent.service;

import in.mycodethesaurus.athlenz_strava_agent.dto.ComputedFacts;
import in.mycodethesaurus.athlenz_strava_agent.dto.InsightItem;
import in.mycodethesaurus.athlenz_strava_agent.dto.ModelInsightOutput;
import in.mycodethesaurus.athlenz_strava_agent.dto.SanitizedActivity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class OutputValidator {

    private static final Set<String> VALID_STATUSES = Set.of("OK", "INSUFFICIENT_DATA", "REFUSED");
    private static final Set<String> VALID_METRICS = Set.of(
            "ACTIVITY_COUNT", "TOTAL_DISTANCE_KM", "TOTAL_MOVING_TIME_MIN",
            "LONGEST_ACTIVITY_DISTANCE_KM", "DOMINANT_SPORT");
    private static final Set<String> VALID_CONFIDENCES = Set.of("LOW", "MEDIUM", "HIGH");
    private static final Set<String> VALID_UNITS = Set.of("count", "km", "min", "sport");
    private static final double TOLERANCE = 0.05;

    public List<String> validate(ModelInsightOutput output, ComputedFacts facts,
                                 List<SanitizedActivity> activities) {
        List<String> errors = new ArrayList<>();

        if (output == null) {
            errors.add("Model output was null");
            return errors;
        }

        validateStatus(output, errors);
        validateSummary(output, errors);
        validateInsightsArray(output, facts, activities, errors);

        return errors;
    }

    private void validateStatus(ModelInsightOutput output, List<String> errors) {
        if (output.status() == null || !VALID_STATUSES.contains(output.status())) {
            errors.add("status must be one of OK, INSUFFICIENT_DATA, REFUSED; got: " + output.status());
        }
    }

    private void validateSummary(ModelInsightOutput output, List<String> errors) {
        if (output.summary() == null || output.summary().isEmpty()) {
            errors.add("summary must not be empty");
        } else if (output.summary().length() > 400) {
            errors.add("summary must be at most 400 characters; got " + output.summary().length());
        }
    }

    private void validateInsightsArray(ModelInsightOutput output, ComputedFacts facts,
                                       List<SanitizedActivity> activities, List<String> errors) {
        List<InsightItem> insights = output.insights();
        boolean isOk = "OK".equals(output.status());

        if (isOk) {
            if (insights == null || insights.isEmpty()) {
                errors.add("insights must have 1-5 items when status is OK");
                return;
            }
            if (insights.size() > 5) {
                errors.add("insights must have at most 5 items; got " + insights.size());
            }
        } else {
            if (insights != null && !insights.isEmpty()) {
                errors.add("insights must be empty when status is not OK");
            }
            return;
        }

        Set<String> validIds = buildIdSet(activities);
        Set<String> usedMetrics = new HashSet<>();

        for (int i = 0; i < insights.size(); i++) {
            validateInsightItem(insights.get(i), i, facts, validIds, usedMetrics, errors);
        }
    }

    private void validateInsightItem(InsightItem item, int idx, ComputedFacts facts,
                                     Set<String> validIds, Set<String> usedMetrics, List<String> errors) {
        String prefix = "insights[" + idx + "]";

        if (item.metric() == null || !VALID_METRICS.contains(item.metric())) {
            errors.add(prefix + ".metric is invalid: " + item.metric());
            return;
        }

        if (!usedMetrics.add(item.metric())) {
            errors.add(prefix + ".metric '" + item.metric() + "' is used more than once");
        }

        if (item.text() == null || item.text().isEmpty()) {
            errors.add(prefix + ".text must not be empty");
        } else if (item.text().length() > 300) {
            errors.add(prefix + ".text must be at most 300 characters");
        }

        if (item.confidence() == null || !VALID_CONFIDENCES.contains(item.confidence())) {
            errors.add(prefix + ".confidence must be LOW, MEDIUM or HIGH; got: " + item.confidence());
        }

        if (item.unit() == null || !VALID_UNITS.contains(item.unit())) {
            errors.add(prefix + ".unit must be one of count/km/min/sport; got: " + item.unit());
        }

        validateEvidenceIds(item, prefix, validIds, errors);
        validateValue(item, prefix, facts, errors);
    }

    private void validateEvidenceIds(InsightItem item, String prefix,
                                     Set<String> validIds, List<String> errors) {
        List<Long> evidenceIds = item.evidenceActivityIds();
        if (evidenceIds == null || evidenceIds.isEmpty()) {
            errors.add(prefix + ".evidence_activity_ids must not be empty");
            return;
        }
        for (Long id : evidenceIds) {
            if (id == null || id <= 0) {
                errors.add(prefix + ".evidence_activity_ids contains invalid id: " + id);
            } else if (!validIds.contains(String.valueOf(id))) {
                errors.add(prefix + ".evidence_activity_ids contains id not in input: " + id);
            }
        }
    }

    private void validateValue(InsightItem item, String prefix, ComputedFacts facts, List<String> errors) {
        if (item.value() == null) {
            errors.add(prefix + ".value must not be null");
            return;
        }

        switch (item.metric()) {
            case "ACTIVITY_COUNT" -> {
                double v = toDouble(item.value());
                if (Math.abs(v - facts.activityCount()) > TOLERANCE) {
                    errors.add(prefix + ".value should be " + facts.activityCount() + " for ACTIVITY_COUNT; got " + item.value());
                }
            }
            case "TOTAL_DISTANCE_KM" -> {
                double v = toDouble(item.value());
                if (Math.abs(v - facts.totalDistanceKm()) > TOLERANCE) {
                    errors.add(prefix + ".value should be " + facts.totalDistanceKm() + " for TOTAL_DISTANCE_KM; got " + item.value());
                }
            }
            case "TOTAL_MOVING_TIME_MIN" -> {
                double v = toDouble(item.value());
                if (Math.abs(v - facts.totalMovingTimeMin()) > TOLERANCE) {
                    errors.add(prefix + ".value should be " + facts.totalMovingTimeMin() + " for TOTAL_MOVING_TIME_MIN; got " + item.value());
                }
            }
            case "LONGEST_ACTIVITY_DISTANCE_KM" -> {
                double v = toDouble(item.value());
                if (Math.abs(v - facts.longestActivityDistanceKm()) > TOLERANCE) {
                    errors.add(prefix + ".value should be " + facts.longestActivityDistanceKm() + " for LONGEST_ACTIVITY_DISTANCE_KM; got " + item.value());
                }
                // evidence must include the longest activity
                if (item.evidenceActivityIds() != null) {
                    boolean citedsLongest = item.evidenceActivityIds().stream()
                            .anyMatch(id -> String.valueOf(id).equals(facts.longestActivityId()));
                    if (!citedsLongest) {
                        errors.add(prefix + ".evidence_activity_ids must include the longest activity id: " + facts.longestActivityId());
                    }
                }
            }
            case "DOMINANT_SPORT" -> {
                String v = String.valueOf(item.value());
                if (!facts.dominantSport().equals(v)) {
                    errors.add(prefix + ".value should be '" + facts.dominantSport() + "' for DOMINANT_SPORT; got '" + v + "'");
                }
                // evidence must include at least one dominant sport activity
                if (item.evidenceActivityIds() != null) {
                    Set<String> dominantIds = new HashSet<>(facts.dominantSportActivityIds());
                    boolean citedDominant = item.evidenceActivityIds().stream()
                            .anyMatch(id -> dominantIds.contains(String.valueOf(id)));
                    if (!citedDominant) {
                        errors.add(prefix + ".evidence_activity_ids must include at least one activity of dominant sport '" + facts.dominantSport() + "'");
                    }
                }
            }
            default -> { /* metric already validated above */ }
        }
    }

    private Set<String> buildIdSet(List<SanitizedActivity> activities) {
        Set<String> ids = new HashSet<>();
        for (SanitizedActivity a : activities) {
            ids.add(a.id());
        }
        return ids;
    }

    private double toDouble(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }
}
