package com.turontechnologies.tcoop.config;

import com.turontechnologies.tcoop.audit.AuditTrailInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** MVC interceptors. CORS is configured in the security chain instead (see CorsConfig). */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

  private final AuditTrailInterceptor auditTrailInterceptor;

  public WebMvcConfig(AuditTrailInterceptor auditTrailInterceptor) {
    this.auditTrailInterceptor = auditTrailInterceptor;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(auditTrailInterceptor).addPathPatterns("/api/v1/**");
  }
}
