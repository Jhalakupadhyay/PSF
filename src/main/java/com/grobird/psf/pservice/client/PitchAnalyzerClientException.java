package com.grobird.psf.pservice.client;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class PitchAnalyzerClientException extends ResponseStatusException {

    public PitchAnalyzerClientException(String message) {
        super(HttpStatus.BAD_GATEWAY, message);
    }

    public PitchAnalyzerClientException(String message, Throwable cause) {
        super(HttpStatus.BAD_GATEWAY, message, cause);
    }
}
