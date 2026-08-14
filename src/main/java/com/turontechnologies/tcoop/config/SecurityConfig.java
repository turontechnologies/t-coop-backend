package com.turontechnologies.tcoop.config;

import com.turontechnologies.tcoop.auth.JwtAuthenticationFilter;
import com.turontechnologies.tcoop.auth.JwtService;
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

@Configuration
public class SecurityConfig {

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http, JwtService jwtService, JsonAuthenticationEntryPoint authEntryPoint)
      throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(handling -> handling.authenticationEntryPoint(authEntryPoint))
        .authorizeHttpRequests(
            auth ->
                auth
                    // CORS preflight requests never carry the Authorization header, so they
                    // must be let through Security before it ever asks "authenticated?" —
                    // otherwise the browser never sees the Access-Control-Allow-Origin header
                    // that the WebMvcConfigurer-based CorsConfig would have added downstream.
                    .requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .requestMatchers("/api/health", "/api/v1/auth/**", "/error")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(
            new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }
}
