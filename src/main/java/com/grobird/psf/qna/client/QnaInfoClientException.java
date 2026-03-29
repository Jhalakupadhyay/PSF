package com.grobird.psf.qna.client;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class QnaInfoClientException extends ResponseStatusException {

    public QnaInfoClientException(String message) {
        super(HttpStatus.BAD_GATEWAY, message);
    }

    public QnaInfoClientException(String message, Throwable cause) {
        super(HttpStatus.BAD_GATEWAY, message, cause);
    }
}
