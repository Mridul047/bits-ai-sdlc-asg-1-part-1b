package in.mycodethesaurus.athlenz_strava_agent.service;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import in.mycodethesaurus.athlenz_strava_agent.config.InsightsProperties;
import in.mycodethesaurus.athlenz_strava_agent.dto.Activity;
import in.mycodethesaurus.athlenz_strava_agent.exception.ErrorCode;
import in.mycodethesaurus.athlenz_strava_agent.exception.InsightsException;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class FileActivitySource {

    private static final Logger log = LoggerFactory.getLogger(FileActivitySource.class);
    private static final Pattern VALID_NAME = Pattern.compile("^[A-Za-z0-9._-]{1,100}\\.json$");
    private static final int MAX_ACTIVITIES = 200;

    private final InsightsProperties properties;
    private final ObjectMapper objectMapper;

    public FileActivitySource(InsightsProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public List<Activity> load(String sourceName) {
        validateName(sourceName);

        Path baseDir = Path.of(properties.getSourceDir()).toAbsolutePath().normalize();
        Path resolved;
        try {
            resolved = baseDir.resolve(sourceName).toRealPath();
        } catch (NoSuchFileException e) {
            throw new InsightsException(ErrorCode.SOURCE_NOT_FOUND,
                    "Source file not found: " + sourceName);
        } catch (IOException e) {
            throw new InsightsException(ErrorCode.SOURCE_NOT_FOUND,
                    "Cannot access source file: " + sourceName);
        }

        if (!resolved.startsWith(baseDir)) {
            throw new InsightsException(ErrorCode.INVALID_SOURCE_NAME,
                    "Source name must not escape the base directory");
        }

        List<Activity> activities;
        try {
            activities = objectMapper.readValue(resolved.toFile(),
                    new TypeReference<List<Activity>>() {});
        } catch (JacksonException e) {
            throw new InsightsException(ErrorCode.INVALID_SOURCE_CONTENT,
                    "Source file is not a valid JSON array of activities");
        }

        if (activities.isEmpty() || activities.size() > MAX_ACTIVITIES) {
            throw new InsightsException(ErrorCode.INVALID_SOURCE_CONTENT,
                    "Source file must contain 1 to " + MAX_ACTIVITIES + " activities, found " + activities.size());
        }

        validateActivities(activities);
        log.info("Loaded {} activities from {}", activities.size(), sourceName);
        return activities;
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new InsightsException(ErrorCode.INVALID_SOURCE_NAME,
                    "Source name must not be empty");
        }
        if (name.contains("..") || name.contains("/") || name.contains("\\")) {
            throw new InsightsException(ErrorCode.INVALID_SOURCE_NAME,
                    "Source name must not contain path separators or '..'");
        }
        if (!VALID_NAME.matcher(name).matches()) {
            throw new InsightsException(ErrorCode.INVALID_SOURCE_NAME,
                    "Source name must match ^[A-Za-z0-9._-]{1,100}\\.json$");
        }
    }

    private void validateActivities(List<Activity> activities) {
        Set<String> seen = new HashSet<>();
        for (Activity a : activities) {
            if (a.id() == null || a.id().isBlank()) {
                throw new InsightsException(ErrorCode.INVALID_SOURCE_CONTENT,
                        "Activity is missing required field: id");
            }
            if (!seen.add(a.id())) {
                throw new InsightsException(ErrorCode.INVALID_SOURCE_CONTENT,
                        "Duplicate activity id: " + a.id());
            }
            if (a.sportType() == null || a.sportType().isBlank()) {
                throw new InsightsException(ErrorCode.INVALID_SOURCE_CONTENT,
                        "Activity " + a.id() + " is missing required field: sport_type");
            }
            if (a.distance() < 0) {
                throw new InsightsException(ErrorCode.INVALID_SOURCE_CONTENT,
                        "Activity " + a.id() + " has negative distance");
            }
            if (a.movingTime() < 0) {
                throw new InsightsException(ErrorCode.INVALID_SOURCE_CONTENT,
                        "Activity " + a.id() + " has negative moving_time");
            }
            if (a.startDate() == null || a.startDate().isBlank()) {
                throw new InsightsException(ErrorCode.INVALID_SOURCE_CONTENT,
                        "Activity " + a.id() + " is missing required field: start_date");
            }
        }
    }
}
