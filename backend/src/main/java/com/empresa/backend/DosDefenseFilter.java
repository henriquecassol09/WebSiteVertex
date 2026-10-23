package com.empresa.backend;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class DosDefenseFilter extends OncePerRequestFilter {

    private static final long MAX_ALLOWED_BODY_BYTES = 2 * 1024 * 1024;

    @Value("${app.security.rate-limit.auth-capacity:10}")
    private int authCapacity;

    @Value("${app.security.rate-limit.auth-refill-minutes:1}")
    private int authRefillMinutes;

    @Value("${app.security.rate-limit.api-capacity:120}")
    private int apiCapacity;

    @Value("${app.security.rate-limit.api-refill-minutes:1}")
    private int apiRefillMinutes;

    @Value("${app.security.rate-limit.prospeccao-capacity:3}")
    private int prospeccaoCapacity;

    @Value("${app.security.rate-limit.prospeccao-refill-minutes:5}")
    private int prospeccaoRefillMinutes;

    @Value("${app.security.rate-limit.max-concurrent-requests:30}")
    private int maxConcurrentRequests;

    @Value("${app.security.rate-limit.jail-minutes:3}")
    private int jailMinutes;

    private final ConcurrentHashMap<String, AtomicInteger> activeConnectionsPerIp = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> authBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> apiBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> prospeccaoBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> violacoesPorIp = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> quarentenaIps = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        if (path.startsWith("/api/health") || path.startsWith("/actuator/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = clientIp(request);

        // Verificação de quarentena
        Instant expiracaoQuarentena = quarentenaIps.get(ip);
        if (expiracaoQuarentena != null) {
            if (Instant.now().isBefore(expiracaoQuarentena)) {
                long segundosRestantes = Duration.between(Instant.now(), expiracaoQuarentena).toSeconds();
                rejeitarRequisicao(response, 429, segundosRestantes,
                        "IP em quarentena temporaria por excesso abusivo de requisicoes (DoS Defense). Aguarde.");
                return;
            } else {
                quarentenaIps.remove(ip);
                violacoesPorIp.remove(ip);
            }
        }

        // Validação de tamanho máximo do corpo
        long contentLength = request.getContentLengthLong();
        if (contentLength <= 0) {
            String headerCl = request.getHeader("Content-Length");
            if (headerCl != null && !headerCl.isBlank()) {
                try {
                    contentLength = Long.parseLong(headerCl.trim());
                } catch (NumberFormatException ignored) {}
            }
        }

        if (contentLength > MAX_ALLOWED_BODY_BYTES) {
            log.warn("DoS Defense: Payload excessivo detectado de IP {} ({} bytes). Bloqueado.", ip, contentLength);
            response.setStatus(413);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"status\":413,\"erro\":\"Corpo da requisicao excede o limite maximo permitido de 2MB.\"}");
            return;
        }

        // Controle de conexões concorrentes simultâneas
        AtomicInteger conexoesAtivas = activeConnectionsPerIp.computeIfAbsent(ip, k -> new AtomicInteger(0));
        int ativas = conexoesAtivas.incrementAndGet();

        try {
            if (ativas > maxConcurrentRequests) {
                log.warn("DoS Defense: Limite de conexões concorrentes excedido para o IP {} ({} conexões ativas).", ip, ativas);
                registrarViolacao(ip);
                rejeitarRequisicao(response, 429, 5,
                        "Muitas conexoes simultaneas ativas a partir deste IP. Reduza o volume concorrente.");
                return;
            }

            // Rate limiting por rota
            boolean isAuth = path.startsWith("/api/auth/");
            boolean isProspeccao = path.equals("/api/pesquisas/prospectar-regiao");

            Bucket bucket;
            int limiteCapacidade;

            if (isProspeccao) {
                bucket = prospeccaoBuckets.computeIfAbsent(ip, k -> novoBucket(prospeccaoCapacity, prospeccaoRefillMinutes));
                limiteCapacidade = prospeccaoCapacity;
            } else if (isAuth) {
                bucket = authBuckets.computeIfAbsent(ip, k -> novoBucket(authCapacity, authRefillMinutes));
                limiteCapacidade = authCapacity;
            } else {
                bucket = apiBuckets.computeIfAbsent(ip, k -> novoBucket(apiCapacity, apiRefillMinutes));
                limiteCapacidade = apiCapacity;
            }

            if (bucket.tryConsume(1)) {
                response.setHeader("X-RateLimit-Limit", String.valueOf(limiteCapacidade));
                response.setHeader("X-RateLimit-Remaining", String.valueOf(bucket.getAvailableTokens()));
                filterChain.doFilter(request, response);
            } else {
                registrarViolacao(ip);
                long retryAfter = isProspeccao ? 10 : 5;
                rejeitarRequisicao(response, 429, retryAfter,
                        "Muitas requisicoes. Limite de taxa excedido. Tente novamente mais tarde.");
            }

        } finally {
            conexoesAtivas.decrementAndGet();
        }
    }

    private void registrarViolacao(String ip) {
        int contagem = violacoesPorIp.computeIfAbsent(ip, k -> new AtomicInteger(0)).incrementAndGet();
        if (contagem >= 20) {
            log.error("DoS Defense: IP {} atingiu {} violacoes. Aplicando quarentena temporaria de {} minutos.",
                    ip, contagem, jailMinutes);
            quarentenaIps.put(ip, Instant.now().plus(Duration.ofMinutes(jailMinutes)));
        }
    }

    private void rejeitarRequisicao(HttpServletResponse response, int status, long retryAfterSeconds, String mensagem) throws IOException {
        response.setStatus(status);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(String.format("{\"status\":%d,\"erro\":\"%s\",\"retryAfterSegundos\":%d}",
                status, mensagem, retryAfterSeconds));
    }

    private Bucket novoBucket(int capacity, int refillMinutes) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillIntervally(capacity, Duration.ofMinutes(refillMinutes))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    // Limpeza de buckets e quarentenas expiradas
    @Scheduled(fixedRate = 300000)
    public void limparBucketsInativos() {
        if (authBuckets.size() > 5000) authBuckets.clear();
        if (apiBuckets.size() > 5000) apiBuckets.clear();
        if (prospeccaoBuckets.size() > 2000) prospeccaoBuckets.clear();

        Instant agora = Instant.now();
        quarentenaIps.entrySet().removeIf(entry -> agora.isAfter(entry.getValue()));
        violacoesPorIp.entrySet().removeIf(entry -> !quarentenaIps.containsKey(entry.getKey()));
        activeConnectionsPerIp.entrySet().removeIf(entry -> entry.getValue().get() <= 0);
    }
}
