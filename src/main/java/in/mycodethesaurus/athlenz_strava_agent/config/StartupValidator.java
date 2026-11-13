package in.mycodethesaurus.athlenz_strava_agent.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public class StartupValidator {

    private static final Logger log = LoggerFactory.getLogger(StartupValidator.class);

    @Value("${BEDROCK_MODEL_ID:}")
    private String bedrockModelId;

    @Value("${AWS_REGION:}")
    private String awsRegion;

    private final InsightsProperties insightsProperties;

    public StartupValidator(InsightsProperties insightsProperties) {
        this.insightsProperties = insightsProperties;
    }

    @PostConstruct
    public void validate() {
        List<String> problems = new ArrayList<>();

        if (awsRegion.isBlank()) problems.add("AWS_REGION environment variable is not set");
        if (bedrockModelId.isBlank()) problems.add("BEDROCK_MODEL_ID environment variable is not set");

        String sourceDir = insightsProperties.getSourceDir();
        if (sourceDir == null || sourceDir.isBlank()) {
            problems.add("insights.source-dir (INSIGHTS_SOURCE_DIR) is not configured");
        } else if (!Files.isDirectory(Path.of(sourceDir))) {
            problems.add("insights.source-dir '" + sourceDir + "' does not exist or is not a directory");
        }

        if (!problems.isEmpty()) {
            String message = "Application cannot start. Missing required configuration:\n" +
                    String.join("\n", problems.stream().map(p -> "  - " + p).toList());
            log.error(message);
            throw new IllegalStateException(message);
        }

        log.info("Startup validation passed. source-dir={}", sourceDir);
    }
}
