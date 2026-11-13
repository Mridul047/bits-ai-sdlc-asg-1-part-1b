package in.mycodethesaurus.athlenz_strava_agent.service;

import in.mycodethesaurus.athlenz_strava_agent.dto.Activity;
import in.mycodethesaurus.athlenz_strava_agent.dto.ComputedFacts;
import in.mycodethesaurus.athlenz_strava_agent.dto.InsightItem;
import in.mycodethesaurus.athlenz_strava_agent.dto.InsightsRequest;
import in.mycodethesaurus.athlenz_strava_agent.dto.InsightsResponse;
import in.mycodethesaurus.athlenz_strava_agent.dto.ModelInsightOutput;
import in.mycodethesaurus.athlenz_strava_agent.dto.SanitizedActivity;
import in.mycodethesaurus.athlenz_strava_agent.exception.ErrorCode;
import in.mycodethesaurus.athlenz_strava_agent.exception.InsightsException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** @see "FR-006, FR-009, FR-010: InsightsService orchestration" */
@ExtendWith(MockitoExtension.class)
class InsightsServiceTest {

    @Mock private FileActivitySource fileActivitySource;
    @Mock private ActivitySanitizer activitySanitizer;
    @Mock private FactsCalculator factsCalculator;
    @Mock private InsightsModelClient modelClient;
    @Mock private OutputValidator outputValidator;

    @InjectMocks
    private InsightsService insightsService;

    private static final String SOURCE = "activities.json";

    private List<Activity> threeActivities() {
        return List.of(
                new Activity("1", "Run A", "Run", 5000.0, 1500L, "2024-01-01T07:00:00Z"),
                new Activity("2", "Run B", "Run", 7000.0, 1800L, "2024-01-02T07:00:00Z"),
                new Activity("3", "Ride", "Ride", 10000.0, 2400L, "2024-01-03T08:00:00Z")
        );
    }

    private List<SanitizedActivity> threeSanitized() {
        return List.of(
                new SanitizedActivity("1", "Run A", "Run", 5000.0, 1500L, "2024-01-01T07:00:00Z"),
                new SanitizedActivity("2", "Run B", "Run", 7000.0, 1800L, "2024-01-02T07:00:00Z"),
                new SanitizedActivity("3", "Ride", "Ride", 10000.0, 2400L, "2024-01-03T08:00:00Z")
        );
    }

    private ComputedFacts threeActivityFacts() {
        return new ComputedFacts(3, 22.0, 95.0, 10.0, "3", "Run",
                List.of("1", "2"), List.of("1", "2", "3"));
    }

    private ModelInsightOutput validOkOutput() {
        InsightItem item = new InsightItem("ACTIVITY_COUNT", "You have 3 activities.", "HIGH",
                List.of(1L, 2L, 3L), 3, "count");
        return new ModelInsightOutput("OK", "Summary of 3 activities.", List.of(item));
    }

    @Test
    @Tag("FR-006")
    void insufficientDataReturnedWhenFewerThanThreeActivities() {
        List<Activity> twoActivities = List.of(
                new Activity("1", "Run", "Run", 5000.0, 1500L, "2024-01-01T07:00:00Z"),
                new Activity("2", "Ride", "Ride", 10000.0, 2400L, "2024-01-02T08:00:00Z")
        );
        List<SanitizedActivity> twoSanitized = List.of(
                new SanitizedActivity("1", "Run", "Run", 5000.0, 1500L, "2024-01-01T07:00:00Z"),
                new SanitizedActivity("2", "Ride", "Ride", 10000.0, 2400L, "2024-01-02T08:00:00Z")
        );
        ComputedFacts twoFacts = new ComputedFacts(2, 15.0, 65.0, 10.0, "2", "Ride",
                List.of("2"), List.of("1", "2"));

        when(fileActivitySource.load(SOURCE)).thenReturn(twoActivities);
        when(activitySanitizer.sanitize(twoActivities)).thenReturn(twoSanitized);
        when(factsCalculator.compute(twoSanitized)).thenReturn(twoFacts);

        InsightsResponse response = insightsService.generateInsights(new InsightsRequest(SOURCE, null));

        assertThat(response.status()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(response.insights()).isEmpty();
        assertThat(response.metadata().activitiesAnalysed()).isEqualTo(2);
        verify(modelClient, never()).call(any(), any(), any());
    }

    @Test
    @Tag("FR-006")
    void insufficientDataReturnedForEmptyActivityArray() {
        when(fileActivitySource.load(SOURCE)).thenReturn(Collections.emptyList());
        when(activitySanitizer.sanitize(any())).thenReturn(Collections.emptyList());
        when(factsCalculator.compute(any()))
                .thenReturn(new ComputedFacts(0, 0.0, 0.0, 0.0, null, null,
                        Collections.emptyList(), Collections.emptyList()));

        InsightsResponse response = insightsService.generateInsights(new InsightsRequest(SOURCE, null));

        assertThat(response.status()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(response.insights()).isEmpty();
        assertThat(response.metadata().activitiesAnalysed()).isZero();
        verify(modelClient, never()).call(any(), any(), any());
    }

    @Test
    @Tag("FR-006")
    void modelNotCalledWhenInsufficientData() {
        when(fileActivitySource.load(SOURCE)).thenReturn(List.of(
                new Activity("1", "Run", "Run", 5000.0, 1500L, "2024-01-01T07:00:00Z")
        ));
        when(activitySanitizer.sanitize(any())).thenReturn(List.of(
                new SanitizedActivity("1", "Run", "Run", 5000.0, 1500L, "2024-01-01T07:00:00Z")
        ));
        when(factsCalculator.compute(any())).thenReturn(new ComputedFacts(1, 5.0, 25.0, 5.0, "1", "Run",
                List.of("1"), List.of("1")));

        insightsService.generateInsights(new InsightsRequest(SOURCE, null));

        verify(modelClient, never()).call(any(), any(), any());
        verify(modelClient, never()).callWithFeedback(any(), any(), any(), any());
    }

    @Test
    @Tag("FR-009")
    void successfulFirstCallReturnsResponse() {
        when(fileActivitySource.load(SOURCE)).thenReturn(threeActivities());
        when(activitySanitizer.sanitize(any())).thenReturn(threeSanitized());
        when(factsCalculator.compute(any())).thenReturn(threeActivityFacts());
        when(modelClient.call(any(), any(), any())).thenReturn(validOkOutput());
        when(outputValidator.validate(any(), any(), any())).thenReturn(Collections.emptyList());

        InsightsResponse response = insightsService.generateInsights(new InsightsRequest(SOURCE, "How am I doing?"));

        assertThat(response.status()).isEqualTo("OK");
        assertThat(response.insights()).hasSize(1);
        assertThat(response.metadata().source()).isEqualTo(SOURCE);
        assertThat(response.metadata().activitiesAnalysed()).isEqualTo(3);
    }

    @Test
    @Tag("FR-009")
    void retriesOnceWhenFirstOutputFailsValidation() {
        when(fileActivitySource.load(SOURCE)).thenReturn(threeActivities());
        when(activitySanitizer.sanitize(any())).thenReturn(threeSanitized());
        when(factsCalculator.compute(any())).thenReturn(threeActivityFacts());
        when(modelClient.call(any(), any(), any())).thenReturn(validOkOutput());
        when(modelClient.callWithFeedback(any(), any(), any(), anyList())).thenReturn(validOkOutput());
        when(outputValidator.validate(any(), any(), any()))
                .thenReturn(List.of("value mismatch")) // first call fails
                .thenReturn(Collections.emptyList());   // retry succeeds

        InsightsResponse response = insightsService.generateInsights(new InsightsRequest(SOURCE, null));

        assertThat(response.status()).isEqualTo("OK");
        verify(modelClient, times(1)).call(any(), any(), any());
        verify(modelClient, times(1)).callWithFeedback(any(), any(), any(), anyList());
    }

    @Test
    @Tag("FR-009")
    void throwsModelOutputInvalidAfterRetryStillFails() {
        when(fileActivitySource.load(SOURCE)).thenReturn(threeActivities());
        when(activitySanitizer.sanitize(any())).thenReturn(threeSanitized());
        when(factsCalculator.compute(any())).thenReturn(threeActivityFacts());
        when(modelClient.call(any(), any(), any())).thenReturn(validOkOutput());
        when(modelClient.callWithFeedback(any(), any(), any(), anyList())).thenReturn(validOkOutput());
        when(outputValidator.validate(any(), any(), any()))
                .thenReturn(List.of("value mismatch"))  // first call fails
                .thenReturn(List.of("still wrong"));     // retry also fails

        assertThatThrownBy(() ->
                insightsService.generateInsights(new InsightsRequest(SOURCE, null)))
                .isInstanceOf(InsightsException.class)
                .satisfies(ex -> assertThat(((InsightsException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.MODEL_OUTPUT_INVALID));
    }

    @Test
    @Tag("FR-002")
    void throwsInvalidQuestionWhenQuestionTooLong() {
        String tooLong = "q".repeat(301);
        assertThatThrownBy(() ->
                insightsService.generateInsights(new InsightsRequest(SOURCE, tooLong)))
                .isInstanceOf(InsightsException.class)
                .satisfies(ex -> assertThat(((InsightsException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_QUESTION));
    }

    @Test
    @Tag("FR-010")
    void metadataIncludesSourceAndCount() {
        when(fileActivitySource.load(SOURCE)).thenReturn(threeActivities());
        when(activitySanitizer.sanitize(any())).thenReturn(threeSanitized());
        when(factsCalculator.compute(any())).thenReturn(threeActivityFacts());
        when(modelClient.call(any(), any(), any())).thenReturn(validOkOutput());
        when(outputValidator.validate(any(), any(), any())).thenReturn(Collections.emptyList());

        InsightsResponse response = insightsService.generateInsights(new InsightsRequest(SOURCE, null));

        assertThat(response.metadata().source()).isEqualTo(SOURCE);
        assertThat(response.metadata().activitiesAnalysed()).isEqualTo(3);
    }

    @Test
    @Tag("FR-007")
    void refusedStatusPassesThroughFromModel() {
        when(fileActivitySource.load(SOURCE)).thenReturn(threeActivities());
        when(activitySanitizer.sanitize(any())).thenReturn(threeSanitized());
        when(factsCalculator.compute(any())).thenReturn(threeActivityFacts());
        when(modelClient.call(any(), any(), any())).thenReturn(
                new ModelInsightOutput("REFUSED", "This question is out of scope.", Collections.emptyList()));
        when(outputValidator.validate(any(), any(), any())).thenReturn(Collections.emptyList());

        InsightsResponse response = insightsService.generateInsights(
                new InsightsRequest(SOURCE, "What is the weather today?"));

        assertThat(response.status()).isEqualTo("REFUSED");
        assertThat(response.insights()).isEmpty();
    }

    @Test
    @Tag("FR-012")
    void propagatesModelErrorException() {
        when(fileActivitySource.load(SOURCE)).thenReturn(threeActivities());
        when(activitySanitizer.sanitize(any())).thenReturn(threeSanitized());
        when(factsCalculator.compute(any())).thenReturn(threeActivityFacts());
        when(modelClient.call(any(), any(), any()))
                .thenThrow(new InsightsException(ErrorCode.MODEL_ERROR, "Provider failed"));

        assertThatThrownBy(() ->
                insightsService.generateInsights(new InsightsRequest(SOURCE, null)))
                .isInstanceOf(InsightsException.class)
                .satisfies(ex -> assertThat(((InsightsException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.MODEL_ERROR));
    }

    @Test
    @Tag("FR-012")
    void propagatesModelTimeoutException() {
        when(fileActivitySource.load(SOURCE)).thenReturn(threeActivities());
        when(activitySanitizer.sanitize(any())).thenReturn(threeSanitized());
        when(factsCalculator.compute(any())).thenReturn(threeActivityFacts());
        when(modelClient.call(any(), any(), any()))
                .thenThrow(new InsightsException(ErrorCode.MODEL_TIMEOUT, "Timed out"));

        assertThatThrownBy(() ->
                insightsService.generateInsights(new InsightsRequest(SOURCE, null)))
                .isInstanceOf(InsightsException.class)
                .satisfies(ex -> assertThat(((InsightsException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.MODEL_TIMEOUT));
    }
}
