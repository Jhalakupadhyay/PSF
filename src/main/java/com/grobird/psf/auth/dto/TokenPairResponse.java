package com.grobird.psf.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.grobird.psf.user.dto.UserDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TokenPairResponse {
    private final String accessToken;
    private final String refreshToken;
    private final UserDTO user;
}
