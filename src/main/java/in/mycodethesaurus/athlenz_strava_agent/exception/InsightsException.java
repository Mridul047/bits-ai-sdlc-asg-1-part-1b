package in.mycodethesaurus.athlenz_strava_agent.exception;

public class InsightsException extends RuntimeException {

    private final ErrorCode errorCode;

    public InsightsException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
