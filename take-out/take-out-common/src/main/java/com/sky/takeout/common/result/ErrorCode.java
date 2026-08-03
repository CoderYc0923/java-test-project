package com.sky.takeout.common.result;

public final class ErrorCode {
    public static final Integer SUCCESS = 1;
    public static final Integer ERROR = 500;
    public static final Integer UNAUTHORIZED = 401;
    public static final Integer USER_NOT_FOUND = 40401;
    public static final Integer PASSWORD_ERROR = 40001;
    public static final Integer FORBIDDEN = 403;
    public static final Integer NOT_FOUND = 404;
    public static final Integer METHOD_NOT_ALLOWED = 405;
    public static final Integer CONFLICT = 409;
    public static final Integer TOO_MANY_REQUESTS = 429;
    public static final Integer INTERNAL_SERVER_ERROR = 500;
    public static final Integer BAD_REQUEST = 400;

    private ErrorCode() {
    }
}
