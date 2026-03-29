package com.grobird.psf.config.redis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
@Profile("!local & !test")
public class RedisConfig {

    /**
     * Explicit bean definition.  Spring Boot's auto-config creates one too,
     * but it eagerly pings the broker.  Defining it here gives us control
     * and the @Bean is lazy by default in Spring 6 / Boot 3.x.
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(connectionFactory);
        return template;
    }
}
