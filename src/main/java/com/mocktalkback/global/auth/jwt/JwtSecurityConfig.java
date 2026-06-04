package com.mocktalkback.global.auth.jwt;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

@Configuration
public class JwtSecurityConfig {

    @Bean
    SecretKey jwtSecretKey(@Value("${JWT_SECRET}") String secret) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalArgumentException("JWT_SECRET must be at least 32 bytes for HS256.");
        }
        return new SecretKeySpec(secretBytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    @Bean
    @Qualifier("mocktalkJwtDecoder")
    JwtDecoder mocktalkJwtDecoder(
            SecretKey jwtSecretKey,
            @Value("${JWT_ISSUER:mocktalk}") String issuer
    ) {
        return buildDecoder(jwtSecretKey, issuer, null);
    }

    @Bean
    @Qualifier("accessJwtDecoder")
    JwtDecoder accessJwtDecoder(
            SecretKey jwtSecretKey,
            @Value("${JWT_ISSUER:mocktalk}") String issuer,
            MocktalkAccessTokenValidator accessTokenValidator
    ) {
        return buildDecoder(jwtSecretKey, issuer, accessTokenValidator);
    }

    @Bean
    MocktalkAccessTokenValidator mocktalkAccessTokenValidator() {
        return new MocktalkAccessTokenValidator();
    }

    @Bean
    MocktalkJwtAuthenticationConverter mocktalkJwtAuthenticationConverter() {
        return new MocktalkJwtAuthenticationConverter();
    }

    private static NimbusJwtDecoder buildDecoder(
            SecretKey jwtSecretKey,
            String issuer,
            OAuth2TokenValidator<Jwt> extraValidator
    ) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(issuer);
        OAuth2TokenValidator<Jwt> validator = extraValidator == null
                ? issuerValidator
                : new DelegatingOAuth2TokenValidator<>(issuerValidator, extraValidator);
        decoder.setJwtValidator(validator);
        return decoder;
    }
}