package com.empresa.backend;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableAsync
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class Config {

    private final DosDefenseFilter dosDefenseFilter;
    private final CsrfDefenseFilter csrfDefenseFilter;
    private final SqlInjectionDefenseFilter sqlInjectionDefenseFilter;
    private final XssDefenseFilter xssDefenseFilter;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Value("${app.security.cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            .headers(headers -> headers
                .contentTypeOptions(withDefaults -> {})
                .frameOptions(frame -> frame.deny())
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .preload(true)
                    .maxAgeInSeconds(31536000))
                .referrerPolicy(rp -> rp.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                    "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com data:; img-src 'self' data: https: blob:; connect-src 'self' https:; frame-ancestors 'none'; object-src 'none'; base-uri 'self'; form-action 'self'"))
                .permissionsPolicy(p -> p.policy(
                    "geolocation=(), microphone=(), camera=(), payment=()"))
            )

            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/health").permitAll()
                .requestMatchers(
                    "/api/auth/registrar",
                    "/api/auth/login",
                    "/api/auth/refresh",
                    "/api/auth/verificar-codigo-cadastro",
                    "/api/auth/reenviar-codigo-cadastro",
                    "/api/auth/recuperar-senha",
                    "/api/auth/redefinir-senha",
                    "/api/auth/validar-email",
                    "/api/auth/csrf-token",
                    "/api/suporte/chamado"
                ).permitAll()
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .anyRequest().authenticated()
            )

            // Cadeia de filtros de segurança
            .addFilterBefore(dosDefenseFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(csrfDefenseFilter, DosDefenseFilter.class)
            .addFilterAfter(sqlInjectionDefenseFilter, CsrfDefenseFilter.class)
            .addFilterAfter(xssDefenseFilter, SqlInjectionDefenseFilter.class)
            .addFilterAfter(jwtAuthenticationFilter, XssDefenseFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();

        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Requested-With", "Origin", "X-CSRF-Token", "X-CSRF-Protection"));
        configuration.setExposedHeaders(List.of("Authorization", "X-CSRF-Token"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}

// Expiração periódica de sessões
@Component
@RequiredArgsConstructor
class LimpezaSessoesScheduler {

    private final SessaoRepository sessaoRepository;

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void limparSessoesExpiradas() {
        sessaoRepository.limparExpiradas(Instant.now());
    }
}
