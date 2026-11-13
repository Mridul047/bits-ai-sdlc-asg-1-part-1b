package in.mycodethesaurus.athlenz_strava_agent.dto;

import java.util.List;

public record ModelInsightOutput(
        String status,
        String summary,
        List<InsightItem> insights
) {}
