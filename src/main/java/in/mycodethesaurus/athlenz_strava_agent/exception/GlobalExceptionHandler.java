package in.mycodethesaurus.athlenz_strava_agent.exception;

import in.mycodethesaurus.athlenz_strava_agent.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InsightsException.class)
    public ResponseEntity<ErrorResponse> handleInsightsException(InsightsException ex) {
        log.warn("InsightsException [{}]: {}", ex.getErrorCode(), ex.getMessage());
        var body = new ErrorResponse(ex.getErrorCode().name(), ex.getMessage());
        return ResponseEntity.status(ex.getErrorCode().getHttpStatus()).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        var body = new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred");
        return ResponseEntity.internalServerError().body(body);
    }
}
