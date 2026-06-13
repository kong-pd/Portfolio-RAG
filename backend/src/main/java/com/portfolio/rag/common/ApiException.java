package com.portfolio.rag.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Service-layer exception carrying a contract error code and HTTP status.
 */
@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
}
