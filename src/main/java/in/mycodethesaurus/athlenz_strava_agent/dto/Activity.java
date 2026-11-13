package in.mycodethesaurus.athlenz_strava_agent.dto;

public record Activity(
        String id,
        String name,
        String sportType,
        double distance,
        long movingTime,
        String startDate
) {}
