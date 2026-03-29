package com.grobird.psf.video.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SkillsetResponse {

    private Long id;
    private String name;
    private String videoPlaybackUrl;
    /** Present only when returned from create (POST /skillsets); for initial upload. */
    private String uploadUrl;
    /** Present only when returned from create (POST /skillsets); send back in save video. */
    private String key;
}
