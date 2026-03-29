package com.grobird.psf.config.qna;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableConfigurationProperties(QnaInfoProperties.class)
public class QnaInfoClientConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean(name = "qnaInfoRestTemplate")
    public RestTemplate qnaInfoRestTemplate(QnaInfoProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofMillis(properties.getConnectTimeoutMs()));
        factory.setReadTimeout(java.time.Duration.ofMillis(properties.getReadTimeoutMs()));
        return new RestTemplate(factory);
    }
}
