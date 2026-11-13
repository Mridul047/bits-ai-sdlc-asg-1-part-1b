package in.mycodethesaurus.athlenz_strava_agent.dto;

import java.util.List;

public record InsightItem(
        String metric,
        String text,
        String confidence,
        List<Long> evidenceActivityIds,
        Object value,
        String unit
) {}
