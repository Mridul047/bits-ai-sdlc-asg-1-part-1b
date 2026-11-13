package in.mycodethesaurus.athlenz_strava_agent.dto;

import java.util.List;

public record InsightsResponse(
        String status,
        String summary,
        List<InsightItem> insights,
        Metadata metadata
) {}
