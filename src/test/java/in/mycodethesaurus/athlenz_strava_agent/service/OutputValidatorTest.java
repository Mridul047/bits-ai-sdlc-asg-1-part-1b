package in.mycodethesaurus.athlenz_strava_agent.service;

import in.mycodethesaurus.athlenz_strava_agent.dto.ComputedFacts;
import in.mycodethesaurus.athlenz_strava_agent.dto.InsightItem;
import in.mycodethesaurus.athlenz_strava_agent.dto.ModelInsightOutput;
import in.mycodethesaurus.athlenz_strava_agent.dto.SanitizedActivity;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** @see "INV-001 to INV-004 (FR-010)" */
class OutputValidatorTest {

    private final OutputValidator validator = new OutputValidator();

    private static final ComputedFacts FACTS = new ComputedFacts(
            5, 61.0, 240.0, 30.0, "2",
            "Run", List.of("1", "2", "4", "5"), List.of("1", "2", "3", "4", "5")
    );

    private static final List<SanitizedActivity> ACTIVITIES = List.of(
            new SanitizedActivity("1", "Run A", "Run", 5000.0, 1500L, "2024-01-01T07:00:00Z"),
            new SanitizedActivity("2", "Long Run", "Run", 30000.0, 7200L, "2024-01-02T07:00:00Z"),
            new SanitizedActivity("3", "Ride", "Ride", 10000.0, 2400L, "2024-01-03T08:00:00Z"),
            new SanitizedActivity("4", "Run B", "Run", 7000.0, 1800L, "2024-01-04T18:00:00Z"),
            new SanitizedActivity("5", "Run C", "Run", 9000.0, 1500L, "2024-01-05T07:00:00Z")
    );

    private InsightItem activityCountItem() {
        return new InsightItem("ACTIVITY_COUNT", "You completed 5 activities.", "HIGH",
                List.of(1L, 2L, 3L, 4L, 5L), 5, "count");
    }

    private InsightItem longestItem() {
        return new InsightItem("LONGEST_ACTIVITY_DISTANCE_KM", "Your longest run was 30.0 km.", "HIGH",
                List.of(2L), 30.0, "km");
    }

    private InsightItem dominantSportItem() {
        return new InsightItem("DOMINANT_SPORT", "Your dominant sport is running.", "HIGH",
                List.of(1L, 2L, 4L, 5L), "Run", "sport");
    }

    @Test
    @Tag("INV-001")
    void validOkResponsePassesValidation() {
        ModelInsightOutput output = new ModelInsightOutput("OK", "Great stats!", List.of(activityCountItem()));
        assertThat(validator.validate(output, FACTS, ACTIVITIES)).isEmpty();
    }

    @Test
    @Tag("INV-001")
    void nullOutputReturnsError() {
        assertThat(validator.validate(null, FACTS, ACTIVITIES)).isNotEmpty();
    }

    @Test
    @Tag("INV-001")
    void invalidStatusReturnsError() {
        ModelInsightOutput output = new ModelInsightOutput("UNKNOWN", "summary", Collections.emptyList());
        assertThat(validator.validate(output, FACTS, ACTIVITIES)).isNotEmpty();
    }

    @Test
    @Tag("INV-001")
    void summaryTooLongReturnsError() {
        String tooLong = "x".repeat(401);
        ModelInsightOutput output = new ModelInsightOutput("OK", tooLong, List.of(activityCountItem()));
        assertThat(validator.validate(output, FACTS, ACTIVITIES)).isNotEmpty();
    }

    @Test
    @Tag("INV-006")
    void okStatusRequiresNonEmptyInsights() {
        ModelInsightOutput output = new ModelInsightOutput("OK", "summary", Collections.emptyList());
        assertThat(validator.validate(output, FACTS, ACTIVITIES)).isNotEmpty();
    }

    @Test
    @Tag("INV-006")
    void nonOkStatusRequiresEmptyInsights() {
        ModelInsightOutput output = new ModelInsightOutput("REFUSED", "summary", List.of(activityCountItem()));
        assertThat(validator.validate(output, FACTS, ACTIVITIES)).isNotEmpty();
    }

    @Test
    @Tag("INV-006")
    void insufficientDataStatusWithEmptyInsightsPassesValidation() {
        ModelInsightOutput output = new ModelInsightOutput("INSUFFICIENT_DATA", "Not enough data.", Collections.emptyList());
        assertThat(validator.validate(output, FACTS, ACTIVITIES)).isEmpty();
    }

    @Test
    @Tag("INV-002")
    void evidenceIdNotInInputReturnsError() {
        InsightItem item = new InsightItem("ACTIVITY_COUNT", "5 activities", "HIGH",
                List.of(999L), 5, "count");
        ModelInsightOutput output = new ModelInsightOutput("OK", "summary", List.of(item));
        assertThat(validator.validate(output, FACTS, ACTIVITIES)).isNotEmpty();
    }

    @Test
    @Tag("INV-003")
    void wrongValueForActivityCountReturnsError() {
        InsightItem item = new InsightItem("ACTIVITY_COUNT", "3 activities", "HIGH",
                List.of(1L), 3, "count");
        ModelInsightOutput output = new ModelInsightOutput("OK", "summary", List.of(item));
        assertThat(validator.validate(output, FACTS, ACTIVITIES)).isNotEmpty();
    }

    @Test
    @Tag("INV-003")
    void wrongValueForTotalDistanceReturnsError() {
        InsightItem item = new InsightItem("TOTAL_DISTANCE_KM", "50 km", "HIGH",
                List.of(1L), 50.0, "km");
        ModelInsightOutput output = new ModelInsightOutput("OK", "summary", List.of(item));
        assertThat(validator.validate(output, FACTS, ACTIVITIES)).isNotEmpty();
    }

    @Test
    @Tag("INV-004")
    void longestActivityInsightMustCiteLongestId() {
        InsightItem item = new InsightItem("LONGEST_ACTIVITY_DISTANCE_KM", "30 km", "HIGH",
                List.of(1L), 30.0, "km"); // cites id 1, not 2
        ModelInsightOutput output = new ModelInsightOutput("OK", "summary", List.of(item));
        assertThat(validator.validate(output, FACTS, ACTIVITIES)).isNotEmpty();
    }

    @Test
    @Tag("INV-004")
    void longestActivityInsightWithCorrectIdPassesValidation() {
        ModelInsightOutput output = new ModelInsightOutput("OK", "summary", List.of(longestItem()));
        assertThat(validator.validate(output, FACTS, ACTIVITIES)).isEmpty();
    }

    @Test
    @Tag("INV-004")
    void dominantSportInsightMustCiteDominantSportActivity() {
        InsightItem item = new InsightItem("DOMINANT_SPORT", "Running is dominant", "HIGH",
                List.of(3L), "Run", "sport"); // cites id 3 (Ride, not Run)
        ModelInsightOutput output = new ModelInsightOutput("OK", "summary", List.of(item));
        assertThat(validator.validate(output, FACTS, ACTIVITIES)).isNotEmpty();
    }

    @Test
    @Tag("INV-004")
    void dominantSportWrongValueReturnsError() {
        InsightItem item = new InsightItem("DOMINANT_SPORT", "Riding is dominant", "HIGH",
                List.of(1L), "Ride", "sport");
        ModelInsightOutput output = new ModelInsightOutput("OK", "summary", List.of(item));
        assertThat(validator.validate(output, FACTS, ACTIVITIES)).isNotEmpty();
    }

    @Test
    @Tag("INV-001")
    void duplicateMetricReturnsError() {
        ModelInsightOutput output = new ModelInsightOutput("OK", "summary",
                List.of(activityCountItem(), activityCountItem()));
        assertThat(validator.validate(output, FACTS, ACTIVITIES)).isNotEmpty();
    }

    @Test
    @Tag("INV-001")
    void emptyEvidenceIdsReturnsError() {
        InsightItem item = new InsightItem("ACTIVITY_COUNT", "5 activities", "HIGH",
                Collections.emptyList(), 5, "count");
        ModelInsightOutput output = new ModelInsightOutput("OK", "summary", List.of(item));
        assertThat(validator.validate(output, FACTS, ACTIVITIES)).isNotEmpty();
    }

    @Test
    @Tag("INV-001")
    void allFiveMetricsPassValidation() {
        List<InsightItem> insights = List.of(
                activityCountItem(),
                new InsightItem("TOTAL_DISTANCE_KM", "61.0 km total", "HIGH", List.of(1L, 2L, 3L, 4L, 5L), 61.0, "km"),
                new InsightItem("TOTAL_MOVING_TIME_MIN", "240 min total", "HIGH", List.of(1L, 2L, 3L, 4L, 5L), 240.0, "min"),
                longestItem(),
                dominantSportItem()
        );
        ModelInsightOutput output = new ModelInsightOutput("OK", "Full overview", insights);
        assertThat(validator.validate(output, FACTS, ACTIVITIES)).isEmpty();
    }
}
