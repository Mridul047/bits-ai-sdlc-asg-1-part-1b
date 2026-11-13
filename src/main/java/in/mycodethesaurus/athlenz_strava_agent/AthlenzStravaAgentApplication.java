package in.mycodethesaurus.athlenz_strava_agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import in.mycodethesaurus.athlenz_strava_agent.config.InsightsProperties;

@SpringBootApplication
@EnableConfigurationProperties(InsightsProperties.class)
public class AthlenzStravaAgentApplication {

	public static void main(String[] args) {
		SpringApplication.run(AthlenzStravaAgentApplication.class, args);
	}

}
