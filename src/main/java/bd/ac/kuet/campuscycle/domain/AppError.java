package bd.ac.kuet.campuscycle.domain;

/** Safe application error with user message and internal code. Never exposes SQL/tokens. */
public final class AppError extends RuntimeException {
    private final String code;

    public AppError(String code, String safeMessage) {
        super(safeMessage);
        this.code = code;
    }

    public AppError(String code, String safeMessage, Throwable cause) {
        super(safeMessage, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
