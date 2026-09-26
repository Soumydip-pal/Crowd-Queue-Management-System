package com.crowdmanagement.config;

import com.crowdmanagement.security.JwtAuthenticationFilter;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {
  private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

  private final JwtAuthenticationFilter jwtAuthenticationFilter;

  public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
    this.jwtAuthenticationFilter = jwtAuthenticationFilter;
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .csrf(AbstractHttpConfigurer::disable)
        .cors(cors -> {
        })
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/auth/**", "/api/crowd-status", "/api/health", "/ws/**").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/locations", "/api/locations/counters", "/api/counters",
                "/api/counters/**")
            .permitAll()
            .requestMatchers(HttpMethod.GET, "/api/queue/latest", "/api/queue/history", "/api/queue/live",
                "/api/predict/**")
            .permitAll()
            .requestMatchers("/api/analytics/**").hasAnyRole("ADMIN", "MANAGER")
            .requestMatchers("/api/locations", "/api/locations/**", "/api/counters", "/api/counters/**", "/api/queue",
                "/api/camera/**")
            .hasAnyRole("ADMIN", "MANAGER")
            .anyRequest().authenticated())
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource(
      @Value("${app.cors.allowed-origins}") String allowedOrigins) {
    // Trim each origin so "https://a.com, https://b.com" (with a space after
    // the comma) still matches - a stray leading space makes the entry never
    // match the browser's Origin header, which otherwise fails silently as a
    // CORS error with no obvious cause. Blank entries are dropped too.
    List<String> origins = Arrays.stream(allowedOrigins.split(","))
        .map(String::trim)
        .filter(origin -> !origin.isEmpty())
        .toList();
    log.info("CORS allowed origins: {}", origins);

    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(origins);
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    config.setAllowCredentials(true);
    config.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }
}
 
