package com.grobird.psf.cache.dto;

public record RefreshTokenData(
        Long userId,
        Long tenantId,
        int tokenVersion,
        String deviceId
) {}

