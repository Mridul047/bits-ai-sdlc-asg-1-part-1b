package in.mycodethesaurus.athlenz_strava_agent.controller;

import in.mycodethesaurus.athlenz_strava_agent.dto.InsightsRequest;
import in.mycodethesaurus.athlenz_strava_agent.dto.InsightsResponse;
import in.mycodethesaurus.athlenz_strava_agent.service.InsightsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api")
public class InsightsController {

    private static final Logger log = LoggerFactory.getLogger(InsightsController.class);

    private final InsightsService insightsService;

    public InsightsController(InsightsService insightsService) {
        this.insightsService = insightsService;
    }

    @PostMapping("/insights")
    public ResponseEntity<InsightsResponse> generateInsights(@RequestBody InsightsRequest request) {
        MDC.put("reqId", UUID.randomUUID().toString().substring(0, 8));
        long start = System.currentTimeMillis();
        try {
            log.info("POST /api/insights source={} question={}", request.source(),
                    request.question() != null ? "present" : "absent");
            InsightsResponse response = insightsService.generateInsights(request);
            log.info("Response: status={} insights={} elapsed={}ms",
                    response.status(), response.insights().size(), System.currentTimeMillis() - start);
            return ResponseEntity.ok(response);
        } finally {
            MDC.clear();
        }
    }
}
