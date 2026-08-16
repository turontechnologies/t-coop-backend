package com.turontechnologies.tcoop.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.turontechnologies.tcoop.auth.JwtAuthenticationFilter;
import com.turontechnologies.tcoop.auth.JwtService;
import com.turontechnologies.tcoop.cooperative.CooperativeRepository;
import com.turontechnologies.tcoop.member.MemberRepository;
import com.turontechnologies.tcoop.subscription.SubscriptionGateFilter;
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
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
public class SecurityConfig {

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      JwtService jwtService,
      JsonAuthenticationEntryPoint authEntryPoint,
      CorsConfigurationSource corsConfigurationSource,
      MemberRepository memberRepository,
      CooperativeRepository cooperativeRepository,
      ObjectMapper objectMapper)
      throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        // Registers CORS as a filter in the security chain itself (not just an MVC concern) —
        // so even a 401 generated directly by authEntryPoint below (bad/expired token, never
        // reaches MVC dispatch) still comes back with Access-Control-Allow-Origin. See the
        // javadoc on CorsConfig for the full story; this bit it fixes is easy to reintroduce
        // accidentally, so don't remove this line without understanding why it's here.
        .cors(cors -> cors.configurationSource(corsConfigurationSource))
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(handling -> handling.authenticationEntryPoint(authEntryPoint))
        .authorizeHttpRequests(
            auth ->
                auth
                    // CORS preflight requests never carry the Authorization header, so they
                    // must be let through Security before it ever asks "authenticated?".
                    .requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .requestMatchers("/api/health", "/api/v1/auth/**", "/error")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(
            new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class)
        // Must run after JwtAuthenticationFilter so the caller's identity is already resolved
        // into the SecurityContext by the time this checks their co-op's subscription standing.
        .addFilterAfter(
            new SubscriptionGateFilter(memberRepository, cooperativeRepository, objectMapper),
            JwtAuthenticationFilter.class);

    return http.build();
  }
}
