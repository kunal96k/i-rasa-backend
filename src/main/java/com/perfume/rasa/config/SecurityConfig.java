package com.perfume.rasa.config;
 
 import com.perfume.rasa.service.UserService;
 import lombok.RequiredArgsConstructor;
 import org.springframework.context.annotation.Bean;
 import org.springframework.context.annotation.Configuration;
 import org.springframework.security.authentication.AuthenticationManager;
 import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
 import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
 import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
 import org.springframework.security.config.annotation.web.builders.HttpSecurity;
 import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
 import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
 import org.springframework.security.config.http.SessionCreationPolicy;
 import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
 import org.springframework.security.crypto.password.PasswordEncoder;
 import org.springframework.security.web.SecurityFilterChain;
 import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
 import org.springframework.web.cors.CorsConfiguration;
 import org.springframework.web.cors.CorsConfigurationSource;
 import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
 
 import java.util.Arrays;
 import java.util.Collections;
 import java.util.List;
 
 @Configuration
 @EnableWebSecurity
 @EnableMethodSecurity
 public class SecurityConfig {
 
     private final AppProperties appProperties;
 
     public SecurityConfig(AppProperties appProperties) {
         this.appProperties = appProperties;
     }
 
     @Bean
     public PasswordEncoder passwordEncoder() {
         return new BCryptPasswordEncoder();
     }
 
     @Bean
     public DaoAuthenticationProvider authenticationProvider(UserService userService) {
         DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
         provider.setUserDetailsService(userService);
         provider.setPasswordEncoder(passwordEncoder());
         return provider;
     }
 
     @Bean
     public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
         return config.getAuthenticationManager();
     }
 
     @Bean
     public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
         HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
         requestCache.setMatchingRequestParameterName(null);
 
         http
                 .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                 .csrf(AbstractHttpConfigurer::disable)
                 .requestCache(cache -> cache.requestCache(requestCache))
                 .authorizeHttpRequests(auth -> auth
                         .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/subscribe").permitAll()
                         .requestMatchers(
                                 "/admin-employees.html", "/api/admin/employees/**",
                                 "/admin-subscribers.html", "/api/subscribe/**",
                                 "/admin-reports.html", "/api/admin/dashboard/reports/**",
                                 "/admin-coupons.html", "/api/admin/coupons/**"
                        ).hasAnyRole("ADMIN", "SUPERADMIN")
                        .requestMatchers("/admin.html", "/admin-*.html", "/api/admin/**").hasAnyRole("ADMIN", "SUPERADMIN", "EMPLOYEE")
                        .requestMatchers(
                                 "/", "/login", "/register", "/api/auth/**",
                                 "/css/**", "/js/**", "/images/**", "/img/**",
                                 "/fonts/**", "/webjars/**", "/*.html",
                                 "/favicon.ico", "/api/files/**", "/actuator/**")
                        .permitAll()
                        .requestMatchers("/checkout/**", "/order/**", "/account/**", "/profile/**", "/api/profile/**", "/api/cart/**").authenticated()
                        .anyRequest().permitAll())
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/api/auth/login-form")
                        .defaultSuccessUrl("/", true)
                        .failureUrl("/login?error")
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) -> {
                            String uri = request.getRequestURI();
                            if (uri.startsWith("/api/")) {
                                response.setContentType("application/json;charset=UTF-8");
                                response.setStatus(401);
                                response.getWriter().write("{\"success\":false,\"message\":\"Unauthorized: Please log in.\"}");
                            } else {
                                response.sendRedirect("/login");
                            }
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            String uri = request.getRequestURI();
                            String accept = request.getHeader("Accept");
                            if (uri.endsWith(".html") || (accept != null && accept.contains("text/html"))) {
                                response.sendRedirect("/admin.html?accessDenied=true");
                            } else {
                                response.setContentType("application/json;charset=UTF-8");
                                response.setStatus(403);
                                response.getWriter().write("{\"success\":false,\"message\":\"Access Denied\"}");
                            }
                        })
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .maximumSessions(5)
                        .expiredUrl("/login?expired"));

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(appProperties.getCors().getAllowedOriginPatterns());
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(appProperties.getCors().getAllowCredentials());
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
