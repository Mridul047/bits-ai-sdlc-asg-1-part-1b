package in.mycodethesaurus.athlenz_strava_agent.dto;

import java.util.List;

public record ComputedFacts(
        int activityCount,
        double totalDistanceKm,
        double totalMovingTimeMin,
        double longestActivityDistanceKm,
        String longestActivityId,
        String dominantSport,
        List<String> dominantSportActivityIds,
        List<String> allActivityIds
) {}
