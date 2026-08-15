package com.turontechnologies.tcoop.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Allows the deployed frontend (Vercel) — and localhost during development —
 * to call this API from the browser. Origins come from the FRONTEND_ORIGINS
 * env var (comma-separated) so this never needs a code change to add a new
 * preview/prod frontend URL.
 *
 * <p>This is wired into {@link SecurityConfig} via {@code http.cors(...)}, not registered as a
 * {@code WebMvcConfigurer}. That distinction matters: a WebMvcConfigurer's CORS handling only
 * runs when a request reaches Spring MVC's DispatcherServlet — but a 401 generated directly by
 * Spring Security (e.g. {@link JsonAuthenticationEntryPoint} rejecting a bad/expired token) never
 * reaches MVC at all. Without CORS wired into the security filter chain itself, that 401 response
 * would be missing Access-Control-Allow-Origin, and the browser would report it as a generic CORS
 * failure instead of surfacing the real 401 to application code.
 */
@Configuration
public class CorsConfig {

  @Value("${app.cors.allowed-origins}")
  private String allowedOrigins;

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(List.of(allowedOrigins.split(",")));
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("*"));
    configuration.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", configuration);
    return source;
  }
}
