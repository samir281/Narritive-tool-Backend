package com.narrativenews.web;

import org.springframework.http.HttpStatus;

public class DomainException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public DomainException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
    public HttpStatus status() { return status; }
    public String code() { return code; }

    public static DomainException notFound(String code, String msg) { return new DomainException(HttpStatus.NOT_FOUND, code, msg); }
    public static DomainException conflict(String code, String msg) { return new DomainException(HttpStatus.CONFLICT, code, msg); }
    public static DomainException bad(String code, String msg) { return new DomainException(HttpStatus.BAD_REQUEST, code, msg); }
    public static DomainException forbidden(String msg) { return new DomainException(HttpStatus.FORBIDDEN, "FORBIDDEN", msg); }
}
