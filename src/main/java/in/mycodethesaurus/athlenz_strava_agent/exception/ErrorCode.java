package in.mycodethesaurus.athlenz_strava_agent.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INVALID_SOURCE_NAME(HttpStatus.BAD_REQUEST),
    INVALID_QUESTION(HttpStatus.BAD_REQUEST),
    SOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    INVALID_SOURCE_CONTENT(HttpStatus.UNPROCESSABLE_ENTITY),
    MODEL_ERROR(HttpStatus.BAD_GATEWAY),
    MODEL_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT),
    MODEL_OUTPUT_INVALID(HttpStatus.BAD_GATEWAY);

    private final HttpStatus httpStatus;

    ErrorCode(HttpStatus httpStatus) {
        this.httpStatus = httpStatus;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
