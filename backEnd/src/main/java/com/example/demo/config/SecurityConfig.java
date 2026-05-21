package com.example.demo.config;

import com.example.demo.service.CustomUserDetailsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomUserDetailsService customUserDetailsService;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          CustomUserDetailsService customUserDetailsService,
                          PasswordEncoder passwordEncoder) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.customUserDetailsService = customUserDetailsService;
        this.passwordEncoder = passwordEncoder;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Disable CSRF and enable stateless sessions
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Disable all default authentication mechanisms
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)

                // Define authorization rules
                .authorizeHttpRequests(auth -> auth

                        // CRUCIAL: Allow ALL OPTIONS requests to bypass security
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Existing public paths
                        .requestMatchers("/api/users/registro", "/api/users/login").permitAll()
                        .requestMatchers("/api/payments", "/api/payments/**").permitAll()
                        .requestMatchers("/api/workorders", "/api/workorders/**").permitAll()

                        // Invoice sub-routes that require auth (must come before the products permitAll catch-all)
                        .requestMatchers(HttpMethod.POST, "/api/telegram/webhook").permitAll()
                        .requestMatchers("/api/customers/**").hasAnyAuthority("ADMIN", "SUPER_ADMIN", "GESTOR")
                        .requestMatchers("/api/invoices/**").hasAnyAuthority("ADMIN", "SUPER_ADMIN", "GESTOR")
                        .requestMatchers("/api/operator/**").hasAuthority("SUPER_ADMIN")

                        // Public Invoice routes
                        .requestMatchers("/api/products", "/api/products/**", "/api/products/filter").permitAll()

                        // Existing restricted paths
                        .requestMatchers("/error").permitAll()

                        .requestMatchers(HttpMethod.GET, "/api/admin/**").hasAnyAuthority("ADMIN", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/admin/**").hasAnyAuthority("ADMIN", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/admin/**").hasAnyAuthority("ADMIN", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/admin/**").hasAnyAuthority("ADMIN", "SUPER_ADMIN")

                        .requestMatchers(HttpMethod.GET, "/api/costs/**").hasAnyAuthority("ADMIN", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/costs/**").hasAnyAuthority("ADMIN", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/costs/**").hasAnyAuthority("ADMIN", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/costs/**").hasAnyAuthority("ADMIN", "SUPER_ADMIN")

                        .requestMatchers("/api/finance", "/api/finance/**").hasAnyAuthority("ADMIN", "SUPER_ADMIN", "GESTOR")
                        .requestMatchers("/api/ai/**").hasAnyAuthority("ADMIN", "SUPER_ADMIN", "GESTOR")
                        .requestMatchers("/api/payment-options", "/api/payment-options/**").hasAnyAuthority("ADMIN", "SUPER_ADMIN", "GESTOR")

                        .requestMatchers(HttpMethod.GET, "/api/users").hasAnyAuthority("ADMIN", "SUPER_ADMIN")

                        .requestMatchers("/api/users/profile/**", "/api/workOrders/**").authenticated()

                        // Ensure all other paths are secured (Fixing the previous issue I pointed out)
                        .anyRequest().authenticated()
                )

                // Register JWT filter
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

                // Allow H2 console iframe
                .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin));


        return http.build();
    }
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOriginPatterns(List.of("http://localhost:*", "https://**--finanzasdashboard.netlify.app"));


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
