package org.ozonLabel.user.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

// src/main/java/org/ozonlabel/user/config/SecurityConfig.java
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())  // ← ЭТУ СТРОКУ ВЫ ЗАБЫЛИ!
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/user/**").authenticated()  // лучше с /**, на всякий случай
                        .requestMatchers("/api/company/**").authenticated()
                        .requestMatchers("/api/notifications/**").authenticated()
                        .anyRequest().permitAll()
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);                     // Обязательно для cookies/credentials
        config.setAllowedHeaders(List.of("*"));
        config.setAllowedMethods(List.of("*"));

        // ВНИМАНИЕ: Используем allowedOriginPatterns, а НЕ allowedOrigins!
        config.setAllowedOriginPatterns(List.of(
                "http://localhost:3000",
                "https://print-365.ru",
                "http://26.203.217.255:3000"
                // Добавьте сюда все нужные origins, включая будущий продакшн-домен
        ));

        // Если хотите временно разрешить ВСЕ origins (для разработки) — используйте pattern "*":
        config.addAllowedOriginPattern("*");  // Это разрешит любой origin, но с credentials!

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
