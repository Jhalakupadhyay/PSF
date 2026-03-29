package com.grobird.psf.notification.dto;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public enum NotificationStatusFilter {
    ALL,
    UNREAD,
    READ;

    public static NotificationStatusFilter from(String value) {
        if (value == null || value.isBlank()) {
            return ALL;
        }
        try {
            return NotificationStatusFilter.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid notification status. Allowed values: all, unread, read");
        }
    }
}
