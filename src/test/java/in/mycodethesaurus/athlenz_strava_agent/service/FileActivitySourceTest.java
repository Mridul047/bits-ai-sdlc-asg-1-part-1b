package in.mycodethesaurus.athlenz_strava_agent.service;

import in.mycodethesaurus.athlenz_strava_agent.config.InsightsProperties;
import in.mycodethesaurus.athlenz_strava_agent.dto.Activity;
import in.mycodethesaurus.athlenz_strava_agent.exception.ErrorCode;
import in.mycodethesaurus.athlenz_strava_agent.exception.InsightsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** @see "FR-002, FR-003, FR-005: FileActivitySource validation" */
class FileActivitySourceTest {

    @TempDir
    Path tempDir;

    private FileActivitySource source;

    private static final String VALID_JSON = """
            [
              {"id": "1", "name": "Morning Run", "sport_type": "Run", "distance": 5000.0, "moving_time": 1500, "start_date": "2024-01-01T07:00:00Z"},
              {"id": "2", "name": "Ride", "sport_type": "Ride", "distance": 10000.0, "moving_time": 2400, "start_date": "2024-01-02T08:00:00Z"},
              {"id": "3", "name": "Evening Run", "sport_type": "Run", "distance": 7000.0, "moving_time": 1800, "start_date": "2024-01-03T18:00:00Z"}
            ]
            """;

    @BeforeEach
    void setUp() {
        InsightsProperties properties = new InsightsProperties();
        properties.setSourceDir(tempDir.toString());
        ObjectMapper objectMapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .build();
        source = new FileActivitySource(properties, objectMapper);
    }

    private Path writeFile(String name, String content) throws IOException {
        Path file = tempDir.resolve(name);
        Files.writeString(file, content);
        return file;
    }

    @Test
    @Tag("FR-003")
    void loadsValidActivitiesFile() throws IOException {
        writeFile("valid.json", VALID_JSON);
        List<Activity> activities = source.load("valid.json");
        assertThat(activities).hasSize(3);
        assertThat(activities.get(0).id()).isEqualTo("1");
        assertThat(activities.get(0).sportType()).isEqualTo("Run");
    }

    @Test
    @Tag("FR-002")
    void rejectsNullSourceName() {
        assertThatThrownBy(() -> source.load(null))
                .isInstanceOf(InsightsException.class)
                .satisfies(ex -> assertThat(((InsightsException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_SOURCE_NAME));
    }

    @Test
    @Tag("FR-002")
    void rejectsBlankSourceName() {
        assertThatThrownBy(() -> source.load("   "))
                .isInstanceOf(InsightsException.class)
                .satisfies(ex -> assertThat(((InsightsException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_SOURCE_NAME));
    }

    @Test
    @Tag("FR-002")
    void rejectsNameWithPathSeparator() {
        assertThatThrownBy(() -> source.load("sub/file.json"))
                .isInstanceOf(InsightsException.class)
                .satisfies(ex -> assertThat(((InsightsException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_SOURCE_NAME));
    }

    @Test
    @Tag("FR-002")
    void rejectsNameWithParentDirectoryTraversal() {
        assertThatThrownBy(() -> source.load("../secret.json"))
                .isInstanceOf(InsightsException.class)
                .satisfies(ex -> assertThat(((InsightsException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_SOURCE_NAME));
    }

    @Test
    @Tag("FR-002")
    void rejectsNameWithBackslash() {
        assertThatThrownBy(() -> source.load("sub\\file.json"))
                .isInstanceOf(InsightsException.class)
                .satisfies(ex -> assertThat(((InsightsException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_SOURCE_NAME));
    }

    @Test
    @Tag("FR-002")
    void rejectsNameNotEndingInJson() {
        assertThatThrownBy(() -> source.load("activities.csv"))
                .isInstanceOf(InsightsException.class)
                .satisfies(ex -> assertThat(((InsightsException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_SOURCE_NAME));
    }

    @Test
    @Tag("FR-002")
    void rejectsNameExceedingMaxLength() {
        String longName = "a".repeat(101) + ".json";
        assertThatThrownBy(() -> source.load(longName))
                .isInstanceOf(InsightsException.class)
                .satisfies(ex -> assertThat(((InsightsException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_SOURCE_NAME));
    }

    @Test
    @Tag("FR-001")
    void returnsSourceNotFoundWhenFileMissing() {
        assertThatThrownBy(() -> source.load("nonexistent.json"))
                .isInstanceOf(InsightsException.class)
                .satisfies(ex -> assertThat(((InsightsException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.SOURCE_NOT_FOUND));
    }

    @Test
    @Tag("FR-003")
    void rejectsInvalidJsonContent() throws IOException {
        writeFile("bad.json", "this is not json");
        assertThatThrownBy(() -> source.load("bad.json"))
                .isInstanceOf(InsightsException.class)
                .satisfies(ex -> assertThat(((InsightsException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_SOURCE_CONTENT));
    }

    @Test
    @Tag("FR-006")
    void emptyArrayLoadsAsEmptyList() throws IOException {
        // An empty array is schema-valid and must not be rejected. The service
        // turns 0 activities into INSUFFICIENT_DATA (spec FR-006, US3 AS2).
        writeFile("empty.json", "[]");
        List<Activity> activities = source.load("empty.json");
        assertThat(activities).isEmpty();
    }

    @Test
    @Tag("FR-003")
    void rejectsDuplicateActivityIds() throws IOException {
        String withDuplicates = """
                [
                  {"id": "1", "sport_type": "Run", "distance": 5000.0, "moving_time": 1500, "start_date": "2024-01-01T07:00:00Z"},
                  {"id": "1", "sport_type": "Ride", "distance": 10000.0, "moving_time": 2400, "start_date": "2024-01-02T08:00:00Z"}
                ]
                """;
        writeFile("dupes.json", withDuplicates);
        assertThatThrownBy(() -> source.load("dupes.json"))
                .isInstanceOf(InsightsException.class)
                .satisfies(ex -> assertThat(((InsightsException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_SOURCE_CONTENT));
    }

    @Test
    @Tag("FR-003")
    void rejectsMissingSportType() throws IOException {
        String noSport = """
                [
                  {"id": "1", "distance": 5000.0, "moving_time": 1500, "start_date": "2024-01-01T07:00:00Z"}
                ]
                """;
        writeFile("nosport.json", noSport);
        assertThatThrownBy(() -> source.load("nosport.json"))
                .isInstanceOf(InsightsException.class)
                .satisfies(ex -> assertThat(((InsightsException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_SOURCE_CONTENT));
    }

    @Test
    @Tag("FR-003")
    void unknownFieldsAreIgnored() throws IOException {
        String withExtras = """
                [
                  {"id": "1", "sport_type": "Run", "distance": 5000.0, "moving_time": 1500,
                   "start_date": "2024-01-01T07:00:00Z", "start_latlng": [1.0, 2.0], "calories": 300}
                ]
                """;
        writeFile("extras.json", withExtras);
        List<Activity> activities = source.load("extras.json");
        assertThat(activities).hasSize(1);
        assertThat(activities.get(0).id()).isEqualTo("1");
    }

}
