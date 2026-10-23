package com.empresa.backend;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
public class CsrfDefenseFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    @Value("${app.security.cors.allowed-origins:http://localhost:3000,http://localhost:5173,http://127.0.0.1:3000,http://127.0.0.1:5173}")
    private String allowedOrigins;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String method = request.getMethod().toUpperCase();
        String path = request.getRequestURI();

        if (SAFE_METHODS.contains(method)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (path.startsWith("/api/health") || path.startsWith("/actuator/")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Validação de Origin e Referer
        String origin = request.getHeader("Origin");
        String referer = request.getHeader("Referer");

        List<String> origensPermitidas = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(s -> !s.isEmpty())
                .toList();

        if (origin != null && !origin.isBlank()) {
            if (!isOrigemAutorizada(origin, origensPermitidas)) {
                log.warn("CSRF Defense: Bloqueada tentativa de requisicao com Origin cruzada nao autorizada: [{}] no endpoint {}", origin, path);
                rejeitarRequisicaoCsrf(response, "Origem nao autorizada para mutacao de estado (CSRF Protection).");
                return;
            }
        } else if (referer != null && !referer.isBlank()) {
            if (!isRefererAutorizado(referer, origensPermitidas, request.getServerName())) {
                log.warn("CSRF Defense: Bloqueada tentativa de requisicao com Referer suspeito: [{}] no endpoint {}", referer, path);
                rejeitarRequisicaoCsrf(response, "Referer nao autorizado para mutacao de estado (CSRF Protection).");
                return;
            }
        } else {
            String customHeader = request.getHeader("X-Requested-With");
            String csrfHeader = request.getHeader("X-CSRF-Token");
            String csrfProtectionHeader = request.getHeader("X-CSRF-Protection");
            String authHeader = request.getHeader("Authorization");

            boolean temCabecalhoSeguro = (customHeader != null && !customHeader.isBlank())
                    || (csrfHeader != null && !csrfHeader.isBlank())
                    || (csrfProtectionHeader != null && !csrfProtectionHeader.isBlank())
                    || (authHeader != null && authHeader.startsWith("Bearer "));

            String contentType = request.getContentType();
            boolean isFormSubmission = contentType != null && (
                    contentType.toLowerCase().contains("application/x-www-form-urlencoded") ||
                    contentType.toLowerCase().contains("multipart/form-data") ||
                    contentType.toLowerCase().contains("text/plain"));

            if (isFormSubmission && !temCabecalhoSeguro) {
                log.warn("CSRF Defense: Requisicao de formulario simples bloqueada sem headers anti-CSRF no endpoint {}", path);
                rejeitarRequisicaoCsrf(response, "Requisicao rejeitada por falta de cabecalhos de seguranca anti-CSRF.");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isOrigemAutorizada(String origin, List<String> origensPermitidas) {
        String originLimpa = normalizarOrigem(origin);
        for (String permitida : origensPermitidas) {
            if (originLimpa.equalsIgnoreCase(normalizarOrigem(permitida))) {
                return true;
            }
        }
        return false;
    }

    private boolean isRefererAutorizado(String referer, List<String> origensPermitidas, String hostServidor) {
        try {
            URI uri = URI.create(referer);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            int port = uri.getPort();

            if (host == null) return false;

            if (host.equalsIgnoreCase(hostServidor)) {
                return true;
            }

            String refererOrigin = scheme + "://" + host + (port != -1 ? ":" + port : "");
            return isOrigemAutorizada(refererOrigin, origensPermitidas);
        } catch (Exception e) {
            return false;
        }
    }

    private String normalizarOrigem(String uriString) {
        if (uriString == null) return "";
        String s = uriString.trim().toLowerCase();
        if (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    private void rejeitarRequisicaoCsrf(HttpServletResponse response, String mensagem) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(String.format("{\"status\":403,\"erro\":\"%s\"}", mensagem));
    }
}
