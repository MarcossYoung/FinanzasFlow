package com.example.demo.config;

import com.example.demo.service.CustomUserDetailsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final PasswordChangeRequiredFilter passwordChangeRequiredFilter;
    private final CustomUserDetailsService customUserDetailsService;
    private final PasswordEncoder passwordEncoder;

    @Value("${cors.allowed-origins:http://localhost:3000}")
    private String allowedOriginsRaw;

    @Autowired
    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          PasswordChangeRequiredFilter passwordChangeRequiredFilter,
                          CustomUserDetailsService customUserDetailsService,
                          PasswordEncoder passwordEncoder) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.passwordChangeRequiredFilter = passwordChangeRequiredFilter;
        this.customUserDetailsService = customUserDetailsService;
        this.passwordEncoder = passwordEncoder;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(customUserDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Disable CSRF and enable stateless sessions
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authenticationProvider())

                // Disable all default authentication mechanisms
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)

                // Define authorization rules
                .authorizeHttpRequests(auth -> auth

                        // CRUCIAL: Allow ALL OPTIONS requests to bypass security
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Existing public paths
                        .requestMatchers("/api/users/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/users/registro").hasAuthority("SUPER_ADMIN")
                        .requestMatchers("/api/payments", "/api/payments/**").hasAnyAuthority("ADMIN", "GESTOR")
                        .requestMatchers("/api/workorders", "/api/workorders/**").hasAnyAuthority("ADMIN", "GESTOR")

                        // Invoice sub-routes that require auth (must come before the products permitAll catch-all)
                        .requestMatchers(HttpMethod.POST, "/api/telegram/webhook").permitAll()
                        .requestMatchers("/api/customers/**").hasAnyAuthority("ADMIN", "GESTOR")
                        .requestMatchers("/api/invoices/**").hasAnyAuthority("ADMIN", "GESTOR")
                        .requestMatchers("/api/operator/**").hasAuthority("SUPER_ADMIN")

                        // Existing restricted paths
                        .requestMatchers("/error").permitAll()

                        .requestMatchers("/api/admin/telegram/**").hasAuthority("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/admin/**").hasAuthority("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/admin/**").hasAuthority("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/admin/**").hasAuthority("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/admin/**").hasAuthority("ADMIN")

                        .requestMatchers(HttpMethod.GET, "/api/costs/**").hasAuthority("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/costs/**").hasAuthority("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/costs/**").hasAuthority("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/costs/**").hasAuthority("ADMIN")

                        .requestMatchers("/api/finance", "/api/finance/**").hasAnyAuthority("ADMIN", "GESTOR")
                        .requestMatchers("/api/ai/**").hasAnyAuthority("ADMIN", "GESTOR")
                        .requestMatchers("/api/payment-options", "/api/payment-options/**").hasAnyAuthority("ADMIN", "GESTOR")

                        .requestMatchers(HttpMethod.GET, "/api/users").hasAnyAuthority("ADMIN", "SUPER_ADMIN")

                        .requestMatchers("/api/users/profile/**").authenticated()

                        // Ensure all other paths are secured (Fixing the previous issue I pointed out)
                        .anyRequest().authenticated()
                )

                // Register JWT filter
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(passwordChangeRequiredFilter, JwtAuthenticationFilter.class)

                // Allow H2 console iframe
                .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin));


        return http.build();
    }
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        List<String> origins = Arrays.stream(allowedOriginsRaw.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
        config.setAllowedOriginPatterns(origins);


        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L); // cache preflight for 1h

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

}
