package com.grobird.psf.seed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Runs after the application starts. If any user has the placeholder password from
 * {@code seed-dummy-data.sql}, replaces it with a valid BCrypt hash for "Password1!"
 * so login works (e.g. after production was seeded with the SQL script).
 */
@Component
@Order(100)
public class PlaceholderPasswordFixRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlaceholderPasswordFixRunner.class);

    /** Must match the placeholder in src/main/resources/db/seed-dummy-data.sql */
    public static final String PLACEHOLDER_HASH = "$2a$10$8K1p/a0dL1LXMIgoEDFrwOfMQD6y3R2lDcR0TqYhxKPxPxPxPxPxPx";

    private static final String SEED_PASSWORD = "Password1!";

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public PlaceholderPasswordFixRunner(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            String validHash = passwordEncoder.encode(SEED_PASSWORD);
            int updated = jdbcTemplate.update(
                    "UPDATE users SET password = ? WHERE password = ?",
                    validHash, PLACEHOLDER_HASH);
            if (updated > 0) {
                log.info("Fixed placeholder passwords for {} user(s). They can now log in with password: {}", updated, SEED_PASSWORD);
            }
        } catch (Exception e) {
            log.warn("Could not fix placeholder passwords (e.g. users table not yet created): {}", e.getMessage());
        }
    }
}
