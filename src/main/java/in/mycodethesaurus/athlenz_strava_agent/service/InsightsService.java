package in.mycodethesaurus.athlenz_strava_agent.service;

import in.mycodethesaurus.athlenz_strava_agent.dto.Activity;
import in.mycodethesaurus.athlenz_strava_agent.dto.ComputedFacts;
import in.mycodethesaurus.athlenz_strava_agent.dto.InsightsRequest;
import in.mycodethesaurus.athlenz_strava_agent.dto.InsightsResponse;
import in.mycodethesaurus.athlenz_strava_agent.dto.Metadata;
import in.mycodethesaurus.athlenz_strava_agent.dto.ModelInsightOutput;
import in.mycodethesaurus.athlenz_strava_agent.dto.SanitizedActivity;
import in.mycodethesaurus.athlenz_strava_agent.exception.ErrorCode;
import in.mycodethesaurus.athlenz_strava_agent.exception.InsightsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class InsightsService {

    private static final Logger log = LoggerFactory.getLogger(InsightsService.class);
    private static final int MIN_ACTIVITIES_FOR_MODEL = 3;
    private static final int MAX_QUESTION_LENGTH = 300;

    private final FileActivitySource fileActivitySource;
    private final ActivitySanitizer activitySanitizer;
    private final FactsCalculator factsCalculator;
    private final InsightsModelClient modelClient;
    private final OutputValidator outputValidator;

    public InsightsService(FileActivitySource fileActivitySource,
                           ActivitySanitizer activitySanitizer,
                           FactsCalculator factsCalculator,
                           InsightsModelClient modelClient,
                           OutputValidator outputValidator) {
        this.fileActivitySource = fileActivitySource;
        this.activitySanitizer = activitySanitizer;
        this.factsCalculator = factsCalculator;
        this.modelClient = modelClient;
        this.outputValidator = outputValidator;
    }

    public InsightsResponse generateInsights(InsightsRequest request) {
        validateQuestion(request.question());

        List<Activity> activities = fileActivitySource.load(request.source());
        List<SanitizedActivity> sanitized = activitySanitizer.sanitize(activities);
        ComputedFacts facts = factsCalculator.compute(sanitized);
        log.info("Facts: count={} distanceKm={} timeMin={} dominantSport={}",
                facts.activityCount(), facts.totalDistanceKm(),
                facts.totalMovingTimeMin(), facts.dominantSport());

        if (facts.activityCount() < MIN_ACTIVITIES_FOR_MODEL) {
            log.info("INSUFFICIENT_DATA: source={} count={}", request.source(), facts.activityCount());
            return new InsightsResponse(
                    "INSUFFICIENT_DATA",
                    "Not enough activities to generate insights. At least " + MIN_ACTIVITIES_FOR_MODEL + " are required.",
                    Collections.emptyList(),
                    new Metadata(facts.activityCount(), request.source())
            );
        }

        ModelInsightOutput output = callModelWithRetry(facts, sanitized, request.question(), request.source());

        return new InsightsResponse(
                output.status(),
                output.summary(),
                output.insights() != null ? output.insights() : Collections.emptyList(),
                new Metadata(facts.activityCount(), request.source())
        );
    }

    private ModelInsightOutput callModelWithRetry(ComputedFacts facts, List<SanitizedActivity> sanitized,
                                                   String question, String source) {
        log.info("Calling model (attempt 1) source={}", source);
        long t0 = System.currentTimeMillis();
        ModelInsightOutput output = modelClient.call(facts, sanitized, question);
        log.info("Model responded in {}ms status={}", System.currentTimeMillis() - t0, output.status());

        List<String> errors = outputValidator.validate(output, facts, sanitized);
        if (errors.isEmpty()) {
            log.info("Validation passed on first attempt");
            return output;
        }

        log.warn("Validation failed on first attempt errors={} — retrying", errors);
        long t1 = System.currentTimeMillis();
        output = modelClient.callWithFeedback(facts, sanitized, question, errors);
        log.info("Model retry responded in {}ms status={}", System.currentTimeMillis() - t1, output.status());

        errors = outputValidator.validate(output, facts, sanitized);
        if (!errors.isEmpty()) {
            log.error("Model output still invalid after retry source={} errors={}", source, errors);
            throw new InsightsException(ErrorCode.MODEL_OUTPUT_INVALID,
                    "Model returned invalid output after retry");
        }

        log.info("Validation passed after retry");
        return output;
    }

    private void validateQuestion(String question) {
        if (question != null && question.length() > MAX_QUESTION_LENGTH) {
            throw new InsightsException(ErrorCode.INVALID_QUESTION,
                    "Question must not exceed " + MAX_QUESTION_LENGTH + " characters");
        }
    }
}
