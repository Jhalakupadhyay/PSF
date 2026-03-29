package com.grobird.psf.config.video;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(VideoS3Properties.class)
public class VideoS3Config {
}
