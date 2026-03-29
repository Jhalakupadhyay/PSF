package com.grobird.psf.config.pservice;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableConfigurationProperties(PServiceProperties.class)
public class PServiceClientConfig {

    @Bean(name = "pServiceRestTemplate")
    public RestTemplate pServiceRestTemplate(PServiceProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofMillis(properties.getConnectTimeoutMs()));
        factory.setReadTimeout(java.time.Duration.ofMillis(properties.getReadTimeoutMs()));
        return new RestTemplate(factory);
    }
}
