package org.ozonLabel.user.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final CsrfValidationFilter csrfFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Security Headers
                .headers(headers -> headers
                        .contentTypeOptions(contentTypeOptions -> {}) // X-Content-Type-Options: nosniff
                        .frameOptions(frameOptions -> frameOptions.deny()) // X-Frame-Options: DENY
                        .xssProtection(xss -> xss.headerValue(org.springframework.security.web.header.writers.XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .permissionsPolicyHeader(permissions -> permissions.policy(
                                "geolocation=(), microphone=(), camera=(), payment=()"
                        ))
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; " +
                                "img-src 'self' data: https:; font-src 'self'; frame-ancestors 'none'; form-action 'self'"
                        ))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .maxAgeInSeconds(31536000)
                                .preload(true))
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/user/**").authenticated()
                        .requestMatchers("/api/company/**").authenticated()
                        .requestMatchers("/api/notifications/**").authenticated()
                        .requestMatchers("/api/label-sizes/**").authenticated()
                        .anyRequest().permitAll()
                )
                // Add JWT filter before UsernamePasswordAuthenticationFilter
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                // Add CSRF filter after JWT filter
                .addFilterAfter(csrfFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // SECURITY: Only allow specific trusted origins
        configuration.setAllowedOriginPatterns(List.of(
                "https://print-365.ru",
                "https://*.print-365.ru",
                "http://localhost:3000"  // TODO: убрать после разработки
        ));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        // SECURITY: Explicitly list allowed headers instead of wildcard
        configuration.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "X-XSRF-TOKEN",
                "X-Requested-With",
                "Accept",
                "Origin"
        ));
        // Expose CSRF token header to frontend
        configuration.setExposedHeaders(List.of(
                "X-XSRF-TOKEN"
        ));
        configuration.setAllowCredentials(true); // Required for cookies
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}