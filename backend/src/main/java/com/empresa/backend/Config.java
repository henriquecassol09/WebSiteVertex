package com.empresa.backend;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
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
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting simples por IP (in-memory). Para multiplas instancias em
 * producao, substituir por backend distribuido (Redis + bucket4j-redis).
 * Endpoints de autenticacao recebem um limite mais restrito para dificultar
 * forca bruta / credential stuffing.
 */
@Component
class RateLimitFilter extends OncePerRequestFilter {

    @Value("${app.security.rate-limit.auth-capacity}")
    private int authCapacity;

    @Value("${app.security.rate-limit.auth-refill-minutes}")
    private int authRefillMinutes;

    @Value("${app.security.rate-limit.api-capacity}")
    private int apiCapacity;

    @Value("${app.security.rate-limit.api-refill-minutes}")
    private int apiRefillMinutes;

    private final ConcurrentHashMap<String, Bucket> authBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> apiBuckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        // Health check e actuator nao devem ser bloqueados por rate limit
        if (path.startsWith("/api/health") || path.startsWith("/actuator/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = clientIp(request);
        boolean isAuthEndpoint = path.startsWith("/api/auth/");

        Bucket bucket = isAuthEndpoint
                ? authBuckets.computeIfAbsent(ip, k -> novoBucket(authCapacity, authRefillMinutes))
                : apiBuckets.computeIfAbsent(ip, k -> novoBucket(apiCapacity, apiRefillMinutes));

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"erro\":\"Muitas requisicoes. Tente novamente mais tarde.\"}");
        }
    }

    private Bucket novoBucket(int capacity, int refillMinutes) {
        Bandwidth limit = Bandwidth.classic(capacity,
                Refill.intervally(capacity, Duration.ofMinutes(refillMinutes)));
        return Bucket.builder().addLimit(limit).build();
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

@Configuration
@EnableAsync
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class Config {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;

    @Value("${app.security.cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt com fator de custo 12 (equilibrio entre seguranca e performance)
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // API stateless com JWT: CSRF nao se aplica (nenhum cookie de sessao e usado para autenticar)
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
                    "default-src 'self'; frame-ancestors 'none'; object-src 'none'"))
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
                    "/api/auth/codigo-dev",
                    "/api/auth/validar-email"
                ).permitAll()
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .anyRequest().authenticated()
            )

            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(jwtAuthenticationFilter, RateLimitFilter.class);

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
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Requested-With", "Origin"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}

/** Expurga diariamente sessoes (refresh tokens) expiradas da tabela SESSAO. */
@Component
@RequiredArgsConstructor
class LimpezaSessoesScheduler {

    private final SessaoRepository sessaoRepository;

    @Scheduled(cron = "0 0 3 * * *") // 03:00 todo dia
    @Transactional
    public void limparSessoesExpiradas() {
        sessaoRepository.limparExpiradas(Instant.now());
    }
}
