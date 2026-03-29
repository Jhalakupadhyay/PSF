package com.grobird.psf.config;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * One-off: run with Gradle to print a BCrypt hash for the seed password.
 * Usage: ./gradlew runBcryptHash
 */
public class BcryptHashPrinter {

    public static void main(String[] args) {
        String hash = new BCryptPasswordEncoder().encode("Password1!");
        System.out.println(hash);
    }
}
