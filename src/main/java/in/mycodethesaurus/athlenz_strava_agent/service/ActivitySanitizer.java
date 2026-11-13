package in.mycodethesaurus.athlenz_strava_agent.service;

import in.mycodethesaurus.athlenz_strava_agent.dto.Activity;
import in.mycodethesaurus.athlenz_strava_agent.dto.SanitizedActivity;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ActivitySanitizer {

    private static final int MAX_NAME_LENGTH = 100;

    public List<SanitizedActivity> sanitize(List<Activity> activities) {
        return activities.stream()
                .map(this::sanitizeOne)
                .toList();
    }

    private SanitizedActivity sanitizeOne(Activity activity) {
        String name = activity.name();
        if (name != null && name.length() > MAX_NAME_LENGTH) {
            name = name.substring(0, MAX_NAME_LENGTH);
        }
        return new SanitizedActivity(
                activity.id(),
                name,
                activity.sportType(),
                activity.distance(),
                activity.movingTime(),
                activity.startDate()
        );
    }
}
