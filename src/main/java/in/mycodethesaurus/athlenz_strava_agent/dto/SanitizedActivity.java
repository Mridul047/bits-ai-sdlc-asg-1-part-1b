package in.mycodethesaurus.athlenz_strava_agent.dto;

public record SanitizedActivity(
        String id,
        String name,
        String sportType,
        double distance,
        long movingTime,
        String startDate
) {}
