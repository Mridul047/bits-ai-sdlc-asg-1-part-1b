package in.mycodethesaurus.athlenz_strava_agent.service;

import in.mycodethesaurus.athlenz_strava_agent.dto.ComputedFacts;
import in.mycodethesaurus.athlenz_strava_agent.dto.SanitizedActivity;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** @see "FR-005: Statistics computation + rounding" */
class FactsCalculatorTest {

    private final FactsCalculator calculator = new FactsCalculator();

    // Five-activity fixture matching evals/fixtures/five-activities.json
    private List<SanitizedActivity> fiveActivities() {
        return List.of(
                new SanitizedActivity("1", "Morning Run", "Run", 5000.0, 1500L, "2024-01-01T07:00:00Z"),
                new SanitizedActivity("2", "Long Run", "Run", 30000.0, 7200L, "2024-01-02T07:00:00Z"),
                new SanitizedActivity("3", "Ride", "Ride", 10000.0, 2400L, "2024-01-03T08:00:00Z"),
                new SanitizedActivity("4", "Evening Run", "Run", 7000.0, 1800L, "2024-01-04T18:00:00Z"),
                new SanitizedActivity("5", "Morning Run 2", "Run", 9000.0, 1500L, "2024-01-05T07:00:00Z")
        );
    }

    @Test
    @Tag("FR-005")
    void computesActivityCount() {
        ComputedFacts facts = calculator.compute(fiveActivities());
        assertThat(facts.activityCount()).isEqualTo(5);
    }

    @Test
    @Tag("FR-005")
    void computesTotalDistanceInKm() {
        ComputedFacts facts = calculator.compute(fiveActivities());
        // total metres = 5000+30000+10000+7000+9000 = 61000 → 61.0 km
        assertThat(facts.totalDistanceKm()).isEqualTo(61.0);
    }

    @Test
    @Tag("FR-005")
    void computesTotalMovingTimeInMin() {
        ComputedFacts facts = calculator.compute(fiveActivities());
        // total seconds = 1500+7200+2400+1800+1500 = 14400 → 240.0 min
        assertThat(facts.totalMovingTimeMin()).isEqualTo(240.0);
    }

    @Test
    @Tag("FR-005")
    void identifiesLongestActivity() {
        ComputedFacts facts = calculator.compute(fiveActivities());
        assertThat(facts.longestActivityId()).isEqualTo("2");
        assertThat(facts.longestActivityDistanceKm()).isEqualTo(30.0);
    }

    @Test
    @Tag("FR-005")
    void identifiesDominantSport() {
        ComputedFacts facts = calculator.compute(fiveActivities());
        assertThat(facts.dominantSport()).isEqualTo("Run");
        assertThat(facts.dominantSportActivityIds()).containsExactlyInAnyOrder("1", "2", "4", "5");
    }

    @Test
    @Tag("FR-005")
    void roundsDistancesToOneDecimalPlace() {
        // 1500 m = 1.5 km (exact); 1234 m = 1.2 km (rounded down); 1250 m = 1.3 km (round half up)
        List<SanitizedActivity> activities = List.of(
                new SanitizedActivity("1", "A", "Run", 1234.0, 60L, "2024-01-01T00:00:00Z"),
                new SanitizedActivity("2", "B", "Run", 1250.0, 60L, "2024-01-02T00:00:00Z"),
                new SanitizedActivity("3", "C", "Run", 1300.0, 60L, "2024-01-03T00:00:00Z")
        );
        ComputedFacts facts = calculator.compute(activities);
        // total = 3784 m = 3.784 km → 3.8 km (rounds half-up)
        assertThat(facts.totalDistanceKm()).isCloseTo(3.8, within(0.01));
    }

    @Test
    @Tag("FR-005")
    void roundsTimesToOneDecimalPlace() {
        List<SanitizedActivity> activities = List.of(
                new SanitizedActivity("1", "A", "Run", 1000.0, 65L, "2024-01-01T00:00:00Z"),
                new SanitizedActivity("2", "B", "Run", 1000.0, 65L, "2024-01-02T00:00:00Z"),
                new SanitizedActivity("3", "C", "Run", 1000.0, 65L, "2024-01-03T00:00:00Z")
        );
        ComputedFacts facts = calculator.compute(activities);
        // total = 195 s = 3.25 min → 3.3 (round half-up)
        assertThat(facts.totalMovingTimeMin()).isEqualTo(3.3);
    }

    @Test
    @Tag("FR-005")
    void zeroDistanceIsValid() {
        List<SanitizedActivity> activities = List.of(
                new SanitizedActivity("1", "A", "Run", 0.0, 60L, "2024-01-01T00:00:00Z"),
                new SanitizedActivity("2", "B", "Run", 0.0, 60L, "2024-01-02T00:00:00Z"),
                new SanitizedActivity("3", "C", "Run", 0.0, 60L, "2024-01-03T00:00:00Z")
        );
        ComputedFacts facts = calculator.compute(activities);
        assertThat(facts.totalDistanceKm()).isEqualTo(0.0);
    }

    @Test
    @Tag("FR-005")
    void allActivityIdsPresent() {
        ComputedFacts facts = calculator.compute(fiveActivities());
        assertThat(facts.allActivityIds()).containsExactlyInAnyOrder("1", "2", "3", "4", "5");
    }
}
