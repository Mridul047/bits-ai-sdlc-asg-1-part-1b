package in.mycodethesaurus.athlenz_strava_agent.service;

import in.mycodethesaurus.athlenz_strava_agent.dto.ComputedFacts;
import in.mycodethesaurus.athlenz_strava_agent.dto.SanitizedActivity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class FactsCalculator {

    public ComputedFacts compute(List<SanitizedActivity> activities) {
        int count = activities.size();

        double totalDistanceM = activities.stream()
                .mapToDouble(SanitizedActivity::distance)
                .sum();
        double totalMovingTimeS = activities.stream()
                .mapToLong(SanitizedActivity::movingTime)
                .sum();

        double totalDistanceKm = round1dp(totalDistanceM / 1000.0);
        double totalMovingTimeMin = round1dp(totalMovingTimeS / 60.0);

        SanitizedActivity longest = activities.stream()
                .max(Comparator.comparingDouble(SanitizedActivity::distance))
                .orElseThrow();
        double longestDistanceKm = round1dp(longest.distance() / 1000.0);

        // Sport counts
        Map<String, List<String>> sportToIds = new LinkedHashMap<>();
        for (SanitizedActivity a : activities) {
            sportToIds.computeIfAbsent(a.sportType(), k -> new ArrayList<>()).add(a.id());
        }

        String dominantSport = sportToIds.entrySet().stream()
                .max(Comparator.comparingInt(e -> e.getValue().size()))
                .map(Map.Entry::getKey)
                .orElseThrow();

        List<String> dominantIds = sportToIds.get(dominantSport);
        List<String> allIds = activities.stream().map(SanitizedActivity::id).toList();

        return new ComputedFacts(
                count,
                totalDistanceKm,
                totalMovingTimeMin,
                longestDistanceKm,
                longest.id(),
                dominantSport,
                dominantIds,
                allIds
        );
    }

    private static double round1dp(double value) {
        return BigDecimal.valueOf(value)
                .setScale(1, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
