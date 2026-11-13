package in.mycodethesaurus.athlenz_strava_agent.service;

import in.mycodethesaurus.athlenz_strava_agent.dto.ComputedFacts;
import in.mycodethesaurus.athlenz_strava_agent.dto.ModelInsightOutput;
import in.mycodethesaurus.athlenz_strava_agent.dto.SanitizedActivity;
import in.mycodethesaurus.athlenz_strava_agent.exception.ErrorCode;
import in.mycodethesaurus.athlenz_strava_agent.exception.InsightsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeoutException;

@Component
public class InsightsModelClient {

    private static final Logger log = LoggerFactory.getLogger(InsightsModelClient.class);

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final String systemPrompt;

    public InsightsModelClient(ChatClient.Builder chatClientBuilder, ObjectMapper objectMapper) throws IOException {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
        this.systemPrompt = loadSystemPrompt();
    }

    public ModelInsightOutput call(ComputedFacts facts, List<SanitizedActivity> activities, String question) {
        return doCall(facts, activities, question, null);
    }

    public ModelInsightOutput callWithFeedback(ComputedFacts facts, List<SanitizedActivity> activities,
                                               String question, List<String> validationErrors) {
        return doCall(facts, activities, question, validationErrors);
    }

    private ModelInsightOutput doCall(ComputedFacts facts, List<SanitizedActivity> activities,
                                      String question, List<String> validationErrors) {
        String userMessage = buildUserMessage(facts, activities, question, validationErrors);
        log.debug("Calling model for insights (retry={})", validationErrors != null && !validationErrors.isEmpty());
        try {
            return chatClient
                    .prompt()
                    .system(systemPrompt)
                    .user(userMessage)
                    .call()
                    .entity(ModelInsightOutput.class);
        } catch (Exception e) {
            if (isTimeoutCause(e)) {
                log.warn("Model request timed out");
                throw new InsightsException(ErrorCode.MODEL_TIMEOUT, "Model request timed out");
            }
            log.error("Model request failed: {}", e.getClass().getSimpleName(), e);
            throw new InsightsException(ErrorCode.MODEL_ERROR, "Model request failed");
        }
    }

    private String buildUserMessage(ComputedFacts facts, List<SanitizedActivity> activities,
                                    String question, List<String> validationErrors) {
        String factsJson = objectMapper.writeValueAsString(facts);
        String activitiesJson = objectMapper.writeValueAsString(activities);
        String safeQuestion = (question != null && !question.isBlank())
                ? question
                : "(no question — produce general overview for all five metrics)";

        StringBuilder sb = new StringBuilder();
        sb.append("Pre-computed facts (use these exact values for insight values):\n");
        sb.append(factsJson).append("\n\n");
        sb.append("=== UNTRUSTED DATA START ===\n");
        sb.append(activitiesJson).append("\n");
        sb.append("=== UNTRUSTED DATA END ===\n\n");
        sb.append("=== UNTRUSTED QUESTION START ===\n");
        sb.append(safeQuestion).append("\n");
        sb.append("=== UNTRUSTED QUESTION END ===");

        if (validationErrors != null && !validationErrors.isEmpty()) {
            sb.append("\n\nThe previous response failed validation with these errors:\n");
            for (String error : validationErrors) {
                sb.append("- ").append(error).append("\n");
            }
            sb.append("Please correct all issues and return a fully valid response.");
        }

        return sb.toString();
    }

    private boolean isTimeoutCause(Throwable t) {
        if (t == null) return false;
        if (t instanceof TimeoutException) return true;
        String name = t.getClass().getName().toLowerCase();
        if (name.contains("timeout")) return true;
        return isTimeoutCause(t.getCause());
    }

    private String loadSystemPrompt() throws IOException {
        ClassPathResource resource = new ClassPathResource("prompts/insights-system.txt");
        return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }
}
