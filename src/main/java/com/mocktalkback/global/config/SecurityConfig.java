package com.mocktalkback.global.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

import com.mocktalkback.domain.realtime.service.NotificationRealtimeTicketService;
import com.mocktalkback.global.auth.OriginAllowlistFilter;
import com.mocktalkback.global.auth.jwt.JwtAccessDeniedHandler;
import com.mocktalkback.global.auth.jwt.JwtAuthEntryPoint;
import com.mocktalkback.global.auth.jwt.MocktalkJwtAuthenticationConverter;
import com.mocktalkback.global.auth.oauth2.CustomOAuth2UserService;
import com.mocktalkback.global.auth.oauth2.OAuth2LoginFailureHandler;
import com.mocktalkback.global.auth.oauth2.OAuth2LoginSuccessHandler;
import com.mocktalkback.global.auth.realtime.NotificationRealtimeTicketAuthFilter;

import jakarta.servlet.DispatcherType;

@EnableMethodSecurity
@EnableWebSecurity
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    @Bean
    public NotificationRealtimeTicketAuthFilter notificationRealtimeTicketAuthFilter(
        NotificationRealtimeTicketService notificationRealtimeTicketService
    ) {
        return new NotificationRealtimeTicketAuthFilter(notificationRealtimeTicketService);
    }

    @Bean
    @Order(1)
    // OAuth2 로그인 플로우는 인가 요청 상태(state) 등을 유지하기 위해 HttpSession이 필요할 수 있으므로,
    // API(JWT) 인증 체인과 분리해서 세션이 API 인증에 섞이지 않도록 격리한다.
    public SecurityFilterChain oauth2FilterChain(
            HttpSecurity http,
            CustomOAuth2UserService customOAuth2UserService,
            OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler,
            OAuth2LoginFailureHandler oAuth2LoginFailureHandler
    ) throws Exception {
        http
                .securityMatcher("/api/oauth2/**", "/api/login/oauth2/**")
                .csrf(auth -> auth.disable())
                .formLogin(auth -> auth.disable())
                .httpBasic(auth -> auth.disable());

        http
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll());

        http
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED));

        http.oauth2Login(oauth -> oauth
                .authorizationEndpoint(endpoint -> endpoint.baseUri("/api/oauth2/authorization"))
                .redirectionEndpoint(endpoint -> endpoint.baseUri("/api/login/oauth2/code/*"))
                .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                .successHandler(oAuth2LoginSuccessHandler)
                .failureHandler(oAuth2LoginFailureHandler));

        return http.build();
    }

    @Bean
    @Order(2)
    // API 요청은 JWT 기반 무상태(STATELESS)로 유지하여,
    // OAuth2에서 생성된 세션이 API 호출을 "로그인된 것처럼" 인증해버리는 상황을 원천 차단한다.
    public SecurityFilterChain apiFilterChain(
            HttpSecurity http,
            @Qualifier("accessJwtDecoder") JwtDecoder accessJwtDecoder,
            MocktalkJwtAuthenticationConverter mocktalkJwtAuthenticationConverter,
            NotificationRealtimeTicketAuthFilter notificationRealtimeTicketAuthFilter,
            OriginAllowlistFilter originAllowlistFilter,
            JwtAuthEntryPoint jwtAuthEntryPoint,
            JwtAccessDeniedHandler jwtAccessDeniedHandler
    ) throws Exception {
        http
                .csrf(auth -> auth.disable())
                .formLogin(auth -> auth.disable())
                .httpBasic(auth -> auth.disable());

        http
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers("/",
                                "/api/health",
                                "/actuator/prometheus", "/actuator/info", "/actuator/health",
                                "/api/auth/join", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout",
                                "/api/auth/oauth2/callback",
                                "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"
                        )
                        .permitAll()
                        .requestMatchers(HttpMethod.GET,
                            "/api/boards/**", "/api/articles/**", "/api/search/**",
                            "/api/contents/**",
                            "/api/files/*/view",
                            "/api/realtime/boards/**"
                        )
                        .permitAll()
                        .anyRequest().authenticated());

        http
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        http
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(jwtAuthEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler));

        http.oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                        .decoder(accessJwtDecoder)
                        .jwtAuthenticationConverter(mocktalkJwtAuthenticationConverter)));

        http
                .addFilterBefore(originAllowlistFilter, BearerTokenAuthenticationFilter.class)
                .addFilterAfter(notificationRealtimeTicketAuthFilter, BearerTokenAuthenticationFilter.class);

        return http.build();
    }
}