package com.sasoori.exception;

/**
 * Runtime exception that carries an HTTP status code and machine-readable error code.
 * Caught by BaseServlet and serialised to JSON.
 */
public class ApiException extends RuntimeException {

    private final int    httpStatus;
    private final String errorCode;

    public ApiException(int httpStatus, String errorCode, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode  = errorCode;
    }

    // ── Common factory methods ────────────────────────────────────────────

    public static ApiException badRequest(String code, String message) {
        return new ApiException(400, code, message);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(401, "UNAUTHORIZED", message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(403, "FORBIDDEN", message);
    }

    public static ApiException notFound(String message) {
        return new ApiException(404, "NOT_FOUND", message);
    }

    public static ApiException conflict(String code, String message) {
        return new ApiException(409, code, message);
    }

    public static ApiException internal(String message) {
        return new ApiException(500, "INTERNAL_ERROR", message);
    }

    public int getHttpStatus() { return httpStatus; }
    public String getErrorCode() { return errorCode; }
}
